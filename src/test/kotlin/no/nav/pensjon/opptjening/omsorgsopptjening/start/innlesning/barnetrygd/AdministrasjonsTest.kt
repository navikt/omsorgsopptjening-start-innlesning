package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdInnlesing
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Barnetrygdmottaker
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdmottakerService
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdInnlesingRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdmottakerRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

class AdministrasjonsTest : SpringContextTest.NoKafka() {

    @Autowired
    private lateinit var innlesingRepository: BarnetrygdInnlesingRepository

    @Autowired
    private lateinit var barnetrygdmottakerRepository: BarnetrygdmottakerRepository

    @Autowired
    private lateinit var barnetrygdmottakerService: BarnetrygdmottakerService

    companion object {
        const val BEGRUNNELSE = "Fordi jeg vil!"
    }

    @BeforeEach
    fun beforeEach() {
        val innlesing = lagreFullførtInnlesing()
        val barnetrygdmottaker = lagreBarnetrygdMottaker(innlesing)
    }

    @Test
    fun `kan stoppe en melding som er i klar til behandling`() {
        val innlesing = lagreFullførtInnlesing()
        val barnetrygdmottaker = lagreBarnetrygdMottaker(innlesing)
        val stoppResultat = barnetrygdmottakerService.stopp(barnetrygdmottaker.id, BEGRUNNELSE)
        assertThat(stoppResultat).isEqualTo(BarnetrygdmottakerService.StoppResultat.STOPPET)
        barnetrygdmottakerRepository.find(barnetrygdmottaker.id).let {
            assertThat(it).isNotNull()
            assertThat(it!!.status).isInstanceOf(Barnetrygdmottaker.Status.Stoppet::class.java)
            val status = it.status as Barnetrygdmottaker.Status.Stoppet
            assertThat(status.begrunnelse).isEqualTo(BEGRUNNELSE)
        }
    }

    @Test
    fun `kan stoppe en melding som er i retry`() {
        val innlesing = lagreFullførtInnlesing()
        val barnetrygdmottaker = lagreBarnetrygdMottaker(innlesing).retry("Retry").let {
            barnetrygdmottakerRepository.updateStatus(it)
            barnetrygdmottakerRepository.find(it.id)
        }!!
        val stoppResultat = barnetrygdmottakerService.stopp(barnetrygdmottaker.id, BEGRUNNELSE)
        assertThat(stoppResultat).isEqualTo(BarnetrygdmottakerService.StoppResultat.STOPPET)
        barnetrygdmottakerRepository.find(barnetrygdmottaker.id).let {
            assertThat(it).isNotNull()
            assertThat(it!!.status).isInstanceOf(Barnetrygdmottaker.Status.Stoppet::class.java)
            val status = it.status as Barnetrygdmottaker.Status.Stoppet
            assertThat(status.begrunnelse).isEqualTo(BEGRUNNELSE)
        }
    }

    @Test
    fun `vil ikke stoppe en melding som er ferdig`() {
        val innlesing = lagreFullførtInnlesing()
        val barnetrygdmottaker = lagreBarnetrygdMottaker(innlesing).ferdig().let {
            barnetrygdmottakerRepository.updateStatus(it)
            barnetrygdmottakerRepository.find(it.id)
        }!!
        val stoppResultat = barnetrygdmottakerService.stopp(barnetrygdmottaker.id, BEGRUNNELSE)
        assertThat(stoppResultat).isEqualTo(BarnetrygdmottakerService.StoppResultat.ALLEREDE_FERDIG)
        barnetrygdmottakerRepository.find(barnetrygdmottaker.id).let {
            assertThat(it).isNotNull()
            assertThat(it!!.status).isInstanceOf(Barnetrygdmottaker.Status.Ferdig::class.java)
        }
    }

    @Test
    fun `vil stoppe en melding som har feilet`() {
        val innlesing = lagreFullførtInnlesing()
        val barnetrygdmottaker = lagreBarnetrygdMottaker(innlesing).retry("").retry("").retry("").retry("").let {
            assertThat(it.status).isInstanceOf(Barnetrygdmottaker.Status.Feilet::class.java)
            barnetrygdmottakerRepository.updateStatus(it)
            barnetrygdmottakerRepository.find(it.id)
        }!!
        val stoppResultat = barnetrygdmottakerService.stopp(barnetrygdmottaker.id, BEGRUNNELSE)
        assertThat(stoppResultat).isEqualTo(BarnetrygdmottakerService.StoppResultat.STOPPET)
        barnetrygdmottakerRepository.find(barnetrygdmottaker.id).let {
            assertThat(it).isNotNull()
            assertThat(it!!.status).isInstanceOf(Barnetrygdmottaker.Status.Stoppet::class.java)
            val status = it.status as Barnetrygdmottaker.Status.Stoppet
            assertThat(status.begrunnelse).isEqualTo(BEGRUNNELSE)
        }
    }

    @Test
    fun `vil ikke stoppe en melding som er avsluttet`() {
        val innlesing = lagreFullførtInnlesing()
        val barnetrygdmottaker = lagreBarnetrygdMottaker(innlesing).avsluttet("Avsluttet").let {
            barnetrygdmottakerRepository.updateStatus(it)
            barnetrygdmottakerRepository.find(it.id)
        }!!
        val stoppResultat = barnetrygdmottakerService.stopp(barnetrygdmottaker.id, BEGRUNNELSE)
        assertThat(stoppResultat).isEqualTo(BarnetrygdmottakerService.StoppResultat.ALLEREDE_AVSLUTTET)
        barnetrygdmottakerRepository.find(barnetrygdmottaker.id).let {
            assertThat(it).isNotNull()
            assertThat(it!!.status).isInstanceOf(Barnetrygdmottaker.Status.Avsluttet::class.java)
        }
    }

    // -----

    @Test
    fun `kan avslutte en melding som er i klar til behandling`() {
        val innlesing = lagreFullførtInnlesing()
        val barnetrygdmottaker = lagreBarnetrygdMottaker(innlesing)
        val avsluttResultat = barnetrygdmottakerService.avslutt(barnetrygdmottaker.id, BEGRUNNELSE)
        assertThat(avsluttResultat).isEqualTo(BarnetrygdmottakerService.AvsluttResultat.AVSLUTTET)
        barnetrygdmottakerRepository.find(barnetrygdmottaker.id).let {
            assertThat(it).isNotNull()
            assertThat(it!!.status).isInstanceOf(Barnetrygdmottaker.Status.Avsluttet::class.java)
            val status = it.status as Barnetrygdmottaker.Status.Avsluttet
            assertThat(status.begrunnelse).isEqualTo(BEGRUNNELSE)
        }
    }

    @Test
    fun `kan avslutte en melding som er i retry`() {
        val innlesing = lagreFullførtInnlesing()
        val barnetrygdmottaker = lagreBarnetrygdMottaker(innlesing).retry("Retry").let {
            barnetrygdmottakerRepository.updateStatus(it)
            barnetrygdmottakerRepository.find(it.id)
        }!!
        val avsluttResultat = barnetrygdmottakerService.avslutt(barnetrygdmottaker.id, BEGRUNNELSE)
        assertThat(avsluttResultat).isEqualTo(BarnetrygdmottakerService.AvsluttResultat.AVSLUTTET)
        barnetrygdmottakerRepository.find(barnetrygdmottaker.id).let {
            assertThat(it).isNotNull()
            assertThat(it!!.status).isInstanceOf(Barnetrygdmottaker.Status.Avsluttet::class.java)
            val status = it.status as Barnetrygdmottaker.Status.Avsluttet
            assertThat(status.begrunnelse).isEqualTo(BEGRUNNELSE)
        }
    }

    @Test
    fun `vil ikke avslutte en melding som er ferdig`() {
        val innlesing = lagreFullførtInnlesing()
        val barnetrygdmottaker = lagreBarnetrygdMottaker(innlesing).ferdig().let {
            barnetrygdmottakerRepository.updateStatus(it)
            barnetrygdmottakerRepository.find(it.id)
        }!!
        val avsluttResultat = barnetrygdmottakerService.avslutt(barnetrygdmottaker.id, BEGRUNNELSE)
        assertThat(avsluttResultat).isEqualTo(BarnetrygdmottakerService.AvsluttResultat.ALLEREDE_FERDIG)
        barnetrygdmottakerRepository.find(barnetrygdmottaker.id).let {
            assertThat(it).isNotNull()
            assertThat(it!!.status).isInstanceOf(Barnetrygdmottaker.Status.Ferdig::class.java)
        }
    }

    @Test // TODO: må avklares om dette er riktig. En ikke-stoppet melding vil bli med i en "rekjør alle feilede"-operasjon
    fun `vil avslutte en melding som har feilet`() {
        val innlesing = lagreFullførtInnlesing()
        val barnetrygdmottaker = lagreBarnetrygdMottaker(innlesing).retry("").retry("").retry("").retry("").let {
            assertThat(it.status).isInstanceOf(Barnetrygdmottaker.Status.Feilet::class.java)
            barnetrygdmottakerRepository.updateStatus(it)
            barnetrygdmottakerRepository.find(it.id)
        }!!
        val avsluttResultat = barnetrygdmottakerService.avslutt(barnetrygdmottaker.id, BEGRUNNELSE)
        assertThat(avsluttResultat).isEqualTo(BarnetrygdmottakerService.AvsluttResultat.AVSLUTTET)
        barnetrygdmottakerRepository.find(barnetrygdmottaker.id).let {
            assertThat(it).isNotNull()
            assertThat(it!!.status).isInstanceOf(Barnetrygdmottaker.Status.Avsluttet::class.java)
            val status = it.status as Barnetrygdmottaker.Status.Avsluttet
            assertThat(status.begrunnelse).isEqualTo(BEGRUNNELSE)
        }
    }

    fun `kan avslutte en melding som er stoppet`() {
        val innlesing = lagreFullførtInnlesing()
        val barnetrygdmottaker = lagreBarnetrygdMottaker(innlesing).stoppet("Stoppet").let {
            barnetrygdmottakerRepository.updateStatus(it)
            barnetrygdmottakerRepository.find(it.id)
        }!!
        val avsluttResultat = barnetrygdmottakerService.avslutt(barnetrygdmottaker.id, BEGRUNNELSE)
        assertThat(avsluttResultat).isEqualTo(BarnetrygdmottakerService.StoppResultat.ALLEREDE_FERDIG)
        barnetrygdmottakerRepository.find(barnetrygdmottaker.id).let {
            assertThat(it).isNotNull()
            assertThat(it!!.status).isInstanceOf(Barnetrygdmottaker.Status.Avsluttet::class.java)
            val status = it.status as Barnetrygdmottaker.Status.Avsluttet
            assertThat(status.begrunnelse).isEqualTo(BEGRUNNELSE)
        }
    }


    @Test
    fun `kan restarte en melding som er feilet`() {
        val innlesing = lagreFullførtInnlesing()
        val barnetrygdmottaker = lagreBarnetrygdMottaker(innlesing).retry("Feilet").retry("").retry("").retry("").let {
            barnetrygdmottakerRepository.updateStatus(it)
            barnetrygdmottakerRepository.find(it.id)
        }!!
        assertThat(barnetrygdmottaker.status).isInstanceOf(Barnetrygdmottaker.Status.Feilet::class.java)

        val restartResultat = barnetrygdmottakerService.restart(barnetrygdmottaker.id)
        assertThat(restartResultat).isEqualTo(BarnetrygdmottakerService.RestartResultat.RESTARTET)
        barnetrygdmottakerRepository.find(barnetrygdmottaker.id).let {
            assertThat(it).isNotNull()
            assertThat(it!!.status).isInstanceOf(Barnetrygdmottaker.Status.Klar::class.java)
        }
    }


    private fun lagreBarnetrygdMottaker(innlesing: BarnetrygdInnlesing): Barnetrygdmottaker.Mottatt {
        return barnetrygdmottakerRepository.insert(
            Barnetrygdmottaker.Transient(
                ident = "12345678910",
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