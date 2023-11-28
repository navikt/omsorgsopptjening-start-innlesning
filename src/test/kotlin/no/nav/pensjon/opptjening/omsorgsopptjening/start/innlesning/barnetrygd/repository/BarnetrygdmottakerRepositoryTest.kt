package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.SpringContextTest
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdInnlesing
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Barnetrygdmottaker
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.kotlin.given
import org.mockito.kotlin.willReturnConsecutively
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BarnetrygdmottakerRepositoryTest : SpringContextTest.NoKafka() {

    @Autowired
    private lateinit var innlesingRepository: BarnetrygdInnlesingRepository

    @Autowired
    private lateinit var barnetrygdmottakerRepository: BarnetrygdmottakerRepository

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @MockBean
    private lateinit var clock: Clock

    @Test
    @Disabled
    // TODO: Sjekken av status på innlesing gjøres nå i forkant
    fun `finner ingen barnetrygdmottakere som skal prosesseres før alle i forsendelsen er lest inn`() {
        val innlesing = innlesingRepository.bestilt(
            BarnetrygdInnlesing.Bestilt(
                id = InnlesingId.generate(),
                år = 2023,
                forespurtTidspunkt = Instant.now(),
            )
        ).let { innlesingRepository.start(it.startet(1)) }

        given(clock.instant()).willReturn(Instant.now())

        barnetrygdmottakerRepository.insert(
            barnetrygdmottaker = Barnetrygdmottaker.Transient(
                ident = "123",
                correlationId = CorrelationId.generate(),
                innlesingId = innlesing.id
            )
        )

        assertNull(barnetrygdmottakerRepository.finnNesteTilBehandling(innlesing.id))

        innlesingRepository.fullført(innlesing.ferdig())

        assertNotNull(barnetrygdmottakerRepository.finnNesteTilBehandling(innlesing.id))
    }

    @Test
    fun `barnetrygdmottakere havner i karantene i 5 timer dersom de havner i status retry`() {
        val now = Instant.now()

        given(clock.instant()).willReturnConsecutively(
            listOf(
                now, //1
                now.plus(1, ChronoUnit.HOURS), //2
                now.plus(1, ChronoUnit.HOURS), //2
//                now.plus(2, ChronoUnit.HOURS), //2
                now.plus(6, ChronoUnit.HOURS), //3
            )
        )

        val innlesing = lagreFullførtInnlesing()

        val mottaker = barnetrygdmottakerRepository.insert(
            barnetrygdmottaker = Barnetrygdmottaker.Transient(
                ident = "123",
                correlationId = CorrelationId.generate(),
                innlesingId = innlesing.id
            )
        )

        assertNotNull(barnetrygdmottakerRepository.finnNesteTilBehandling(innlesing.id)) //1

        barnetrygdmottakerRepository.updateStatus(mottaker.retry("noe gikk gærnt"))

        assertNull(barnetrygdmottakerRepository.finnNesteTilBehandling(innlesing.id)) //2
        assertNotNull(barnetrygdmottakerRepository.finnNesteTilBehandling(innlesing.id)) //3
    }

    @Test
    fun `finnNesteUprosesserte låser raden slik at den ikke plukkes opp av andre connections`() {
        val innlesing = lagreFullførtInnlesing()

        given(clock.instant()).willReturn(Instant.now())

        barnetrygdmottakerRepository.insert(
            barnetrygdmottaker = Barnetrygdmottaker.Transient(
                ident = "123",
                correlationId = CorrelationId.generate(),
                innlesingId = innlesing.id
            )
        )

        //krev ny transaksjon slik at det opprettes ny connection
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW)

        transactionTemplate.execute {
            //låser den aktuelle raden for denne transaksjonens varighet
            Assertions.assertNotNull(barnetrygdmottakerRepository.finnNesteTilBehandling(innlesing.id))

            //opprett ny transaksjon mens den forrige fortsatt lever
            transactionTemplate.execute {
                //skal ikke finne noe siden raden er låst pga "select for update skip locked"
                Assertions.assertNull(barnetrygdmottakerRepository.finnNesteTilBehandling(innlesing.id))
            }
            //fortsatt samme transaksjon
            Assertions.assertNotNull(barnetrygdmottakerRepository.finnNesteTilBehandling(innlesing.id))
        } //rad ikke låst lenger ved transaksjon slutt


        //ny transaksjon finner raden da den ikke lenger er låst
        transactionTemplate.execute {
            Assertions.assertNotNull(barnetrygdmottakerRepository.finnNesteTilBehandling(innlesing.id))
        }
    }

    @Test
    fun `batch insert setter inn mange rader`() {
        val innlesing = innlesingRepository.bestilt(
            BarnetrygdInnlesing.Bestilt(
                id = InnlesingId.generate(),
                år = 2023,
                forespurtTidspunkt = Instant.now(),
            )
        ).let { innlesingRepository.start(it.startet(1)) }

        val a = Barnetrygdmottaker.Transient(
            ident = "123",
            correlationId = CorrelationId.generate(),
            innlesingId = innlesing.id
        )
        val b = Barnetrygdmottaker.Transient(
            ident = "321",
            correlationId = CorrelationId.generate(),
            innlesingId = innlesing.id
        )

        barnetrygdmottakerRepository.insertBatch(
            listOf(
                a,
                b,
            )
        )

        assertTrue(barnetrygdmottakerRepository.finnAlle(innlesing.id).map { it.ident }
                       .containsAll(listOf("123", "321")))
    }

    @Test
    fun `batch insert kobler sammen riktig barnetrydmottaker og status`() {
        val innlesing = innlesingRepository.bestilt(
            BarnetrygdInnlesing.Bestilt(
                id = InnlesingId.generate(),
                år = 2023,
                forespurtTidspunkt = Instant.now(),
            )
        ).let { innlesingRepository.start(it.startet(1)) }

        val a = Barnetrygdmottaker.Transient(
            ident = "123",
            correlationId = CorrelationId.generate(),
            innlesingId = innlesing.id
        )
        val b = Barnetrygdmottaker.Transient(
            ident = "321",
            correlationId = CorrelationId.generate(),
            innlesingId = innlesing.id
        )

        barnetrygdmottakerRepository.insertBatch(
            listOf(
                a,
                b,
            )
        )

        val alle = barnetrygdmottakerRepository.finnAlle(innlesing.id)

        barnetrygdmottakerRepository.updateStatus(alle.single { it.ident == "123" }.ferdig())
        barnetrygdmottakerRepository.updateStatus(alle.single { it.ident == "321" }.retry("testing"))

        assertInstanceOf(
            Barnetrygdmottaker.Status.Ferdig::class.java,
            barnetrygdmottakerRepository.finnAlle(innlesing.id).single { it.ident == "123" }.status
        )
        assertInstanceOf(
            Barnetrygdmottaker.Status.Retry::class.java,
            barnetrygdmottakerRepository.finnAlle(innlesing.id).single { it.ident == "321" }.status
        )
    }

    private fun lagreFullførtInnlesing(): BarnetrygdInnlesing {
        val bestilt = innlesingRepository.bestilt(
            BarnetrygdInnlesing.Bestilt(
                id = InnlesingId.generate(),
                år = 2023,
                forespurtTidspunkt = Instant.now()
            )
        )
        val startet = innlesingRepository.start(bestilt.startet(1))
        return innlesingRepository.fullført(startet.ferdig())
    }
}