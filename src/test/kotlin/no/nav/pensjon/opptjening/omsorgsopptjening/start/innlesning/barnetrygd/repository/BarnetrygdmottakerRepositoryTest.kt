package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.SpringContextTest
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdInnlesing
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Barnetrygdmottaker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.kotlin.given
import org.mockito.kotlin.willReturnConsecutively
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
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

        assertNull(barnetrygdmottakerRepository.finnNesteTilBehandling(innlesing.id, 1))

        innlesingRepository.fullført(innlesing.ferdig())

        assertNotNull(barnetrygdmottakerRepository.finnNesteTilBehandling(innlesing.id, 1))
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

        assertNotNull(barnetrygdmottakerRepository.finnNesteTilBehandling(innlesing.id, 5)) //1

        barnetrygdmottakerRepository.updateStatus(mottaker.retry("noe gikk gærnt"))

        assertThat(barnetrygdmottakerRepository.finnNesteTilBehandling(innlesing.id, 1).data).isEmpty() //2
        assertNotNull(barnetrygdmottakerRepository.finnNesteTilBehandling(innlesing.id, 5)) //3
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

        transactionTemplate.execute {
            //låser den aktuelle raden for denne transaksjonens varighet
            val locked1 = barnetrygdmottakerRepository.finnNesteTilBehandling(innlesing.id, 5)
            val locked2 = barnetrygdmottakerRepository.finnNesteTilBehandling(innlesing.id, 5)
            barnetrygdmottakerRepository.frigi(locked1)
            val locked3 = barnetrygdmottakerRepository.finnNesteTilBehandling(innlesing.id, 5)
            barnetrygdmottakerRepository.frigi(locked2)
            barnetrygdmottakerRepository.frigi(locked3)

            assertThat(locked1.data).isNotEmpty()
            assertThat(locked2.data).isEmpty()
            assertThat(locked3.data).isNotEmpty()
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

    @Test
    fun `oppdaterer alle feilede rader for en gitt innlesning til klar`() {
        val innlesing = lagreStartetInnlesing()
        val innlesingUberørt = lagreStartetInnlesing()

        lesInnBarnetrygdmottakere(
            Barnetrygdmottaker.Transient(
                ident = "a",
                correlationId = CorrelationId.generate(),
                innlesingId = innlesing.id
            ),
            Barnetrygdmottaker.Transient(
                ident = "b",
                correlationId = CorrelationId.generate(),
                innlesingId = innlesing.id
            )
        )

        lesInnBarnetrygdmottakere(
            Barnetrygdmottaker.Transient(
                ident = "c",
                correlationId = CorrelationId.generate(),
                innlesingId = innlesingUberørt.id
            )
        )

        val alleKlar = barnetrygdmottakerRepository.finnAlle(innlesing.id)
        assertThat(alleKlar.map { it.status }).allMatch { it is Barnetrygdmottaker.Status.Klar }

        barnetrygdmottakerRepository.updateStatus(
            alleKlar.single { it.ident == "a" }.retry("a1").retry("a2").retry("a3").retry("afeil")
        )
        barnetrygdmottakerRepository.updateStatus(
            alleKlar.single { it.ident == "b" }.retry("b1").retry("b2").retry("b3").retry("bfeil")
        )

        val alleFeilet = barnetrygdmottakerRepository.finnAlle(innlesing.id)
        assertThat(alleFeilet.map { it.status }).allMatch { it is Barnetrygdmottaker.Status.Feilet }

        val oppdatert = barnetrygdmottakerRepository.oppdaterFeiledeRaderTilKlar(innlesing.id.toUUID())
        assertThat(oppdatert).isEqualTo(2)
        val alleKlarIgjen = barnetrygdmottakerRepository.finnAlle(innlesing.id)
        assertThat(alleKlarIgjen.map { it.status }).allMatch { it is Barnetrygdmottaker.Status.Klar }

        val first = alleKlarIgjen.first()
        assertThat(first.statushistorikk[0]).isInstanceOf(Barnetrygdmottaker.Status.Klar::class.java)
        assertThat(first.statushistorikk[1]).isInstanceOf(Barnetrygdmottaker.Status.Retry::class.java)
        assertThat(first.statushistorikk[2]).isInstanceOf(Barnetrygdmottaker.Status.Retry::class.java)
        assertThat(first.statushistorikk[3]).isInstanceOf(Barnetrygdmottaker.Status.Retry::class.java)
        assertThat(first.statushistorikk[4]).isInstanceOf(Barnetrygdmottaker.Status.Feilet::class.java)
        assertThat(first.statushistorikk[5]).isInstanceOf(Barnetrygdmottaker.Status.Klar::class.java)

        assertThat(barnetrygdmottakerRepository.finnAlle(innlesingUberørt.id)).hasSize(1)
        assertThat(barnetrygdmottakerRepository.finnAlle(innlesingUberørt.id).single().status).isInstanceOf(
            Barnetrygdmottaker.Status.Klar::class.java
        )
        assertThat(barnetrygdmottakerRepository.finnAlle(innlesingUberørt.id).single().statushistorikk).hasSize(1)
    }

    private fun lagreBestiltInnlesing(
        innlesing: BarnetrygdInnlesing.Bestilt = BarnetrygdInnlesing.Bestilt(
            id = InnlesingId.generate(),
            år = 2023,
            forespurtTidspunkt = Instant.now()
        )
    ): BarnetrygdInnlesing.Bestilt {
        return innlesingRepository.bestilt(innlesing) as BarnetrygdInnlesing.Bestilt
    }

    private fun lagreStartetInnlesing(
        innlesing: BarnetrygdInnlesing.Bestilt = BarnetrygdInnlesing.Bestilt(
            id = InnlesingId.generate(),
            år = 2023,
            forespurtTidspunkt = Instant.now()
        ),
        forventetAntallIdenter: Long = 1
    ): BarnetrygdInnlesing.Startet {
        return innlesingRepository.start(lagreBestiltInnlesing(innlesing).startet(forventetAntallIdenter)) as BarnetrygdInnlesing.Startet
    }

    private fun lesInnBarnetrygdmottakere(vararg barnetrygdmottakere: Barnetrygdmottaker.Transient) {
        barnetrygdmottakerRepository.insertBatch(barnetrygdmottakere.toList())
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