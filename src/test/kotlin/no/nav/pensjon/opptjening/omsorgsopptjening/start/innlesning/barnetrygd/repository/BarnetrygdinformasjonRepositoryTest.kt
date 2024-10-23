package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Rådata
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.RådataFraKilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Kilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Landstilknytning
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Omsorgstype
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.PersongrunnlagMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.SpringContextTest
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdInnlesing
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Barnetrygdinformasjon
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Barnetrygdmottaker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.YearMonth
import java.util.*


class BarnetrygdinformasjonRepositoryTest(
) : SpringContextTest.NoKafka() {

    @Autowired
    lateinit var innlesingRepository: InnlesingRepository

    @Autowired
    lateinit var barnetrygdmottakerRepository: BarnetrygdmottakerRepository

    @Autowired
    lateinit var barnetrygdinformasjonRepository: BarnetrygdinformasjonRepository

    @Test
    fun `kan lagre og hente ut en barnetrygdinformasjon`() {
        val innlesing = lagreFullførtInnlesing()
        val barnetrygdmottaker = lagreBarnetrygdmottaker(innlesing)
        val barnetrygdinformasjon = lagBarnetrygdInformasjon(barnetrygdmottaker, innlesing)
        barnetrygdinformasjonRepository.insert(barnetrygdinformasjon)
        val hentet = barnetrygdinformasjonRepository.hent(barnetrygdinformasjon.id)
        assertThat(hentet).isEqualTo(barnetrygdinformasjon)
    }

    @Test
    fun `kan låse og frigi rader`() {
        val innlesing = lagreFullførtInnlesing()
        val barnetrygdmottaker = lagreBarnetrygdmottaker(innlesing)
        val barnetrygdinformasjon1 = lagBarnetrygdInformasjon(barnetrygdmottaker, innlesing)
        val barnetrygdinformasjon2 = lagBarnetrygdInformasjon(barnetrygdmottaker, innlesing)
        val barnetrygdinformasjon3 = lagBarnetrygdInformasjon(barnetrygdmottaker, innlesing)
        val barnetrygdinformasjon4 = lagBarnetrygdInformasjon(barnetrygdmottaker, innlesing)
        barnetrygdinformasjonRepository.insert(barnetrygdinformasjon1)
        barnetrygdinformasjonRepository.insert(barnetrygdinformasjon2)
        barnetrygdinformasjonRepository.insert(barnetrygdinformasjon3)
        barnetrygdinformasjonRepository.insert(barnetrygdinformasjon4)

        val alle = barnetrygdinformasjonRepository.finnAlle(innlesing.id)
        assertThat(alle).hasSize(4)
        alle.forEach {
            println("::: $it")
        }

        val locked1 = barnetrygdinformasjonRepository.finnNesteTilBehandling(innlesing.id, 3)
        assertThat(locked1.data).hasSize(3)
        val locked2 = barnetrygdinformasjonRepository.finnNesteTilBehandling(innlesing.id, 3)
        assertThat(locked2.data).hasSize(1)
        assertThat(locked1.lockId).isNotEqualTo(locked2.lockId)
        barnetrygdinformasjonRepository.frigi(locked1)
        val locked3 = barnetrygdinformasjonRepository.finnNesteTilBehandling(innlesing.id, 3)
        assertThat(locked3.data).hasSize(3)
        assertThat(locked3.data).containsExactlyElementsOf(locked1.data)
    }


    private fun lagBarnetrygdInformasjon(
        barnetrygdmottaker: Barnetrygdmottaker.Mottatt,
        innlesing: BarnetrygdInnlesing
    ): Barnetrygdinformasjon {
        return Barnetrygdinformasjon(
            id = UUID.randomUUID(),
            barnetrygdmottakerId = barnetrygdmottaker.id,
            ident = "00000000002", // forskjellig fra barnetrygdmottaker, siden den kan være oppdatert
            persongrunnlag = listOf(
                PersongrunnlagMelding.Persongrunnlag(
                    omsorgsyter = "00000000002",
                    omsorgsperioder = listOf(
                        PersongrunnlagMelding.Omsorgsperiode(
                            fom = YearMonth.of(2023, 1),
                            tom = YearMonth.of(2023, 4),
                            omsorgstype = Omsorgstype.FULL_BARNETRYGD,
                            omsorgsmottaker = "10000000001",
                            kilde = Kilde.BARNETRYGD,
                            utbetalt = 2000,
                            landstilknytning = Landstilknytning.NORGE,
                        )
                    ),
                    hjelpestønadsperioder = listOf(
                        PersongrunnlagMelding.Hjelpestønadperiode(
                            fom = YearMonth.of(2023, 5),
                            tom = YearMonth.of(2023, 7),
                            omsorgstype = Omsorgstype.HJELPESTØNAD_FORHØYET_SATS_3,
                            omsorgsmottaker = "10000000002",
                            kilde = Kilde.BARNETRYGD,
                        )
                    )
                )
            ),
            rådata = Rådata(listOf(RådataFraKilde(mapOf("eple" to "eple", "banan" to "banan")))),
            correlationId = barnetrygdmottaker.correlationId.toUUID(),
            innlesingId = innlesing.id.toUUID(),
            status = Barnetrygdinformasjon.Status.KLAR,
        )
    }

    private fun lagreBarnetrygdmottaker(innlesing: BarnetrygdInnlesing): Barnetrygdmottaker.Mottatt {
        return barnetrygdmottakerRepository.insert(
            Barnetrygdmottaker.Transient(
                ident = "00000000001",
                correlationId = CorrelationId.generate(),
                innlesingId = innlesing.id
            )
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