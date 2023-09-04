package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.SpringContextTest
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdInnlesing
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Barnetrygdmottaker
import org.junit.jupiter.api.Assertions
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
    fun `finner ingen barnetrygdmottakere som skal prosesseres før alle i forsendelsen er lest inn`() {
        val innlesing = innlesingRepository.bestilt(
            BarnetrygdInnlesing.Bestilt(
                id = InnlesingId.generate(),
                år = 2023,
                forespurtTidspunkt = Instant.now(),
            )
        ).let { innlesingRepository.start(it.startet()) }

        given(clock.instant()).willReturn(Instant.now())

        barnetrygdmottakerRepository.insert(
            barnetrygdmottaker = Barnetrygdmottaker(
                ident = "123",
                correlationId = CorrelationId.generate(),
                innlesingId = innlesing.id
            )
        )

        assertNull(barnetrygdmottakerRepository.finnNesteUprosesserte())

        innlesingRepository.fullført(innlesing.ferdig())

        assertNotNull(barnetrygdmottakerRepository.finnNesteUprosesserte())
    }

    @Test
    fun `barnetrygdmottakere havner i karantene i 5 timer dersom de havner i status retry`() {
        val now = Instant.now()
        given(clock.instant()).willReturnConsecutively(
            listOf(
                now, //1
                now.plus(3, ChronoUnit.HOURS), //2
                now.plus(6, ChronoUnit.HOURS), //3
            )
        )

        val innlesing = lagreFullførtInnlesing()

        val mottaker = barnetrygdmottakerRepository.insert(
            barnetrygdmottaker = Barnetrygdmottaker(
                ident = "123",
                correlationId = CorrelationId.generate(),
                innlesingId = innlesing.id
            )
        )

        assertNotNull(barnetrygdmottakerRepository.finnNesteUprosesserte()) //1

        barnetrygdmottakerRepository.updateStatus(mottaker.retry("noe gikk gærnt"))

        assertNull(barnetrygdmottakerRepository.finnNesteUprosesserte()) //2
        assertNotNull(barnetrygdmottakerRepository.finnNesteUprosesserte()) //3
    }

    @Test
    fun `finnNesteUprosesserte låser raden slik at den ikke plukkes opp av andre connections`() {
        val innlesing = lagreFullførtInnlesing()

        given(clock.instant()).willReturn(Instant.now())

        barnetrygdmottakerRepository.insert(
            barnetrygdmottaker = Barnetrygdmottaker(
                ident = "123",
                correlationId = CorrelationId.generate(),
                innlesingId = innlesing.id
            )
        )

        //krev ny transaksjon slik at det opprettes ny connection
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW)

        transactionTemplate.execute {
            //låser den aktuelle raden for denne transaksjonens varighet
            Assertions.assertNotNull(barnetrygdmottakerRepository.finnNesteUprosesserte())

            //opprett ny transaksjon mens den forrige fortsatt lever
            transactionTemplate.execute {
                //skal ikke finne noe siden raden er låst pga "select for update skip locked"
                Assertions.assertNull(barnetrygdmottakerRepository.finnNesteUprosesserte())
            }
            //fortsatt samme transaksjon
            Assertions.assertNotNull(barnetrygdmottakerRepository.finnNesteUprosesserte())
        } //rad ikke låst lenger ved transaksjon slutt


        //ny transaksjon finner raden da den ikke lenger er låst
        transactionTemplate.execute {
            Assertions.assertNotNull(barnetrygdmottakerRepository.finnNesteUprosesserte())
        }
    }

    private fun lagreFullførtInnlesing(): BarnetrygdInnlesing {
        val bestilt = innlesingRepository.bestilt(
            BarnetrygdInnlesing.Bestilt(
                id = InnlesingId.generate(),
                år = 2023,
                forespurtTidspunkt = Instant.now()
            )
        )
        val startet = innlesingRepository.start(bestilt.startet())
        return innlesingRepository.fullført(startet.ferdig())
    }
}