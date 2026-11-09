package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.SpringContextTest
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdEndring
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdInnlesing
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Barnetrygdmottaker
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Ident
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.PersonId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Personidentifikator
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.År
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.mockito.kotlin.given
import org.mockito.kotlin.willReturnConsecutively
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

class BarnetrygdmottakerRepositoryTest : SpringContextTest.NoKafka() {

    @Autowired
    private lateinit var innlesingRepository: InnlesingRepository

    @Autowired
    private lateinit var barnetrygdmottakerRepository: BarnetrygdmottakerRepository

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @MockitoBean
    private lateinit var clock: Clock

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
                ident = Ident("123"),
                correlationId = CorrelationId.generate(),
                innlesingId = innlesing.id
            )
        )

        assertThat(barnetrygdmottakerRepository.finnNesteTilBehandling(innlesing.id, 5)) //1.isNotNull()

        barnetrygdmottakerRepository.updateStatus(mottaker.retry("noe gikk gærnt"))

        assertThat(barnetrygdmottakerRepository.finnNesteTilBehandling(innlesing.id, 1).data).isEmpty() //2
        assertThat(barnetrygdmottakerRepository.finnNesteTilBehandling(innlesing.id, 5)) //3.isNotNull()
    }

    @Test
    fun `kan lagre, oppdatere og hente ut en barnetrygdmottaker`() {
        val innlesing = lagreFullførtInnlesing()
        val id = UUID.randomUUID()
        val barnetrygdmottaker = Barnetrygdmottaker.Transient(
            ident = Ident("12345123451"),
            correlationId = CorrelationId.generate(),
            innlesingId = innlesing.id,
        )
        val mottatt = barnetrygdmottakerRepository.insert(barnetrygdmottaker).withPerson(
            PersonId(Ident("12345123452"), setOf("12345123451", "12345123452"))
        )
        barnetrygdmottakerRepository.updatePersonIdent(mottatt)
        val oppdatert = barnetrygdmottakerRepository.find(mottatt.id)!!
        assertThat(oppdatert.personId).isEqualTo(mottatt.personId)
        assertThat(oppdatert).isEqualTo(mottatt)
    }

    @Test
    fun `finnNesteUprosesserte låser raden slik at den ikke plukkes opp av andre connections`() {
        val innlesing = lagreFullførtInnlesing()

        given(clock.instant()).willReturn(Instant.now())

        barnetrygdmottakerRepository.insert(
            barnetrygdmottaker = Barnetrygdmottaker.Transient(
                ident = Ident("123"),
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
        fun fnr(i: Int) = Ident(String.format("%011d", i))
        val innlesing = innlesingRepository.bestilt(
            BarnetrygdInnlesing.Bestilt(
                id = InnlesingId.generate(),
                år = År(2023),
                forespurtTidspunkt = Instant.now(),
            )
        ).let { innlesingRepository.start(it.startet(1)) }

        val a = Barnetrygdmottaker.Transient(
            ident = fnr(1),
            correlationId = CorrelationId.generate(),
            innlesingId = innlesing.id
        )
        val b = Barnetrygdmottaker.Transient(
            ident = fnr(2),
            correlationId = CorrelationId.generate(),
            innlesingId = innlesing.id
        )

        barnetrygdmottakerRepository.insertBatch(
            listOf(
                a,
                b,
            )
        )

        assertThat(barnetrygdmottakerRepository.finnAlle(innlesing.id).map { it.ident })
            .containsExactlyInAnyOrder(fnr(1), fnr(2))
    }

    @Test
    fun `batch insert kobler sammen riktig barnetrydmottaker og status`() {
        fun fnr(i: Int) = Ident(String.format("%011d", i))
        val innlesing = innlesingRepository.bestilt(
            BarnetrygdInnlesing.Bestilt(
                id = InnlesingId.generate(),
                år = År(2023),
                forespurtTidspunkt = Instant.now(),
            )
        ).let { innlesingRepository.start(it.startet(1)) }

        val a = Barnetrygdmottaker.Transient(
            ident = fnr(1),
            correlationId = CorrelationId.generate(),
            innlesingId = innlesing.id
        )
        val b = Barnetrygdmottaker.Transient(
            ident = fnr(2),
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

        barnetrygdmottakerRepository.updateStatus(alle.single { it.ident == fnr(1) }.ferdig())
        barnetrygdmottakerRepository.updateStatus(alle.single { it.ident == fnr(2) }.retry("testing"))

        assertInstanceOf(
            Barnetrygdmottaker.Status.Ferdig::class.java,
            barnetrygdmottakerRepository.finnAlle(innlesing.id).single { it.ident == fnr(1) }.status
        )
        assertInstanceOf(
            Barnetrygdmottaker.Status.Retry::class.java,
            barnetrygdmottakerRepository.finnAlle(innlesing.id).single { it.ident == fnr(2) }.status
        )
    }

    @Test
    fun `oppdaterer alle feilede rader for en gitt innlesning til klar`() {
        fun fnr(i: Int) = Ident(String.format("%011d", i))

        val innlesing = lagreStartetInnlesing()
        val innlesingUberørt = lagreStartetInnlesing()

        lesInnBarnetrygdmottakere(
            Barnetrygdmottaker.Transient(
                ident = fnr(1),
                correlationId = CorrelationId.generate(),
                innlesingId = innlesing.id
            ),
            Barnetrygdmottaker.Transient(
                ident = fnr(2),
                correlationId = CorrelationId.generate(),
                innlesingId = innlesing.id
            )
        )

        lesInnBarnetrygdmottakere(
            Barnetrygdmottaker.Transient(
                ident = fnr(3),
                correlationId = CorrelationId.generate(),
                innlesingId = innlesingUberørt.id
            )
        )

        val alleKlar = barnetrygdmottakerRepository.finnAlle(innlesing.id)
        assertThat(alleKlar.map { it.status }).allMatch { it is Barnetrygdmottaker.Status.Klar }

        barnetrygdmottakerRepository.updateStatus(
            alleKlar.single { it.ident == fnr(1) }.retry("a1").retry("a2").retry("a3").retry("afeil")
        )
        barnetrygdmottakerRepository.updateStatus(
            alleKlar.single { it.ident == fnr(2) }.retry("b1").retry("b2").retry("b3").retry("bfeil")
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

    @Test
    fun `lagrer og henter barnetrygd endring`() {
        val ident = Personidentifikator.random()
        val barnetrygdEndring = BarnetrygdEndring(
            id = null,
            ident = ident,
            vedtakstidspunkt = LocalDateTime.of(2026, Month.NOVEMBER, 16, 15, 13, 12, 123456789),
            endringstidspunkt = LocalDate.of(2022, Month.FEBRUARY, 2),
            sekvensnummer = 42313L,
            opprettet = null,
        )

        given(clock.instant()).willReturn(
            LocalDateTime.of(2026, Month.DECEMBER, 16, 15, 13, 12, 123456789)
                .toInstant(ZoneOffset.UTC),
        )

        barnetrygdmottakerRepository.lagre(listOf(barnetrygdEndring))

        barnetrygdmottakerRepository.hentBarnetrygdEndring(ident).also {
            assertThat(it).hasSize(1)
            it.single().also {
                assertThat(it.id).isNotNull()
                assertThat(it.ident).isEqualTo(ident)
                assertThat(it.vedtakstidspunkt).isEqualTo(
                    LocalDateTime.of(
                        2026,
                        Month.NOVEMBER,
                        16,
                        15,
                        13,
                        12,
                        123457000
                    )
                )
                assertThat(it.endringstidspunkt).isEqualTo(LocalDate.of(2022, Month.FEBRUARY, 2))
                assertThat(it.sekvensnummer).isEqualTo(42313L)
                assertThat(it.opprettet).isEqualTo(
                    LocalDateTime.of(2026, Month.DECEMBER, 16, 15, 13, 12, 123457000).toInstant(ZoneOffset.UTC)
                )
            }
        }
    }

    @Test
    fun `lagring av samme sekvensnummer flere ganger er idempotent`() {
        val ident = Personidentifikator.random()
        val barnetrygdEndring = BarnetrygdEndring(
            id = null,
            ident = ident,
            vedtakstidspunkt = LocalDateTime.of(2026, Month.NOVEMBER, 16, 15, 13, 12, 123456789),
            endringstidspunkt = LocalDate.of(2022, Month.FEBRUARY, 2),
            sekvensnummer = 42313L,
            opprettet = null,
        )

        given(clock.instant()).willReturn(
            LocalDateTime.of(2026, Month.DECEMBER, 16, 15, 13, 12, 123456789)
                .toInstant(ZoneOffset.UTC),
        )

        barnetrygdmottakerRepository.lagre(listOf(barnetrygdEndring))
        val lagret = barnetrygdmottakerRepository.hentBarnetrygdEndring(ident).single()

        given(clock.instant()).willReturn(Instant.parse("2026-12-17T15:13:12Z"))
        barnetrygdmottakerRepository.lagre(
            listOf(barnetrygdEndring.copy(endringstidspunkt = LocalDate.of(2023, Month.MARCH, 3)))
        )

        assertThat(barnetrygdmottakerRepository.hentBarnetrygdEndring(ident)).containsExactly(lagret)
    }

    private fun lagreBestiltInnlesing(
        innlesing: BarnetrygdInnlesing.Bestilt = BarnetrygdInnlesing.Bestilt(
            id = InnlesingId.generate(),
            år = År(2023),
            forespurtTidspunkt = Instant.now()
        )
    ): BarnetrygdInnlesing.Bestilt {
        return innlesingRepository.bestilt(innlesing) as BarnetrygdInnlesing.Bestilt
    }

    private fun lagreStartetInnlesing(
        innlesing: BarnetrygdInnlesing.Bestilt = BarnetrygdInnlesing.Bestilt(
            id = InnlesingId.generate(),
            år = År(2023),
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
                år = År(2023),
                forespurtTidspunkt = Instant.now()
            )
        )
        val startet = innlesingRepository.start(bestilt.startet(1))
        return innlesingRepository.fullført(startet.ferdig())
    }
}