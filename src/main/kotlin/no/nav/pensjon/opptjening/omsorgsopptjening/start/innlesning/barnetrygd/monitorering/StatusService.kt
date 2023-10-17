package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.monitorering

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdInnlesing
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Barnetrygdmottaker.KortStatus
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdInnlesingRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdmottakerRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant.now
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

@Service
class StatusService(
    private val repo: BarnetrygdInnlesingRepository,
    private val mottakerRepo : BarnetrygdmottakerRepository,
    ) {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }

    fun checkStatus(): ApplicationStatus {
        val innlesing = repo.finnSisteInnlesing()
        if (innlesing == null) return ApplicationStatus.IkkeKjort
        else if (forGammel(innlesing)) return ApplicationStatus.Feil("For lenge siden siste innlesing")
        else if (ikkeProsessert(innlesing)) return ApplicationStatus.Feil("Innlesing er ikke prosessert")
        else if (mottakereIkkeProsessert(innlesing)) return ApplicationStatus.Feil("Alle mottakere er ikke prosessert")
        return ApplicationStatus.OK
    }

    private fun ikkeProsessert(innlesing: BarnetrygdInnlesing): Boolean {
        val maksProsesseringsTid = now().minus(2.hours.toJavaDuration())
        val burdeVærtProsessert = innlesing.forespurtTidspunkt < maksProsesseringsTid
        val erIkkeProsessert = innlesing !is BarnetrygdInnlesing.Ferdig
        return burdeVærtProsessert && erIkkeProsessert
    }

    private fun forGammel(innlesing: BarnetrygdInnlesing): Boolean {
        val minimumTidspunkt = now().minus(400.days.toJavaDuration())
        return innlesing.forespurtTidspunkt < minimumTidspunkt
    }

    private fun mottakereIkkeProsessert(innlesing: BarnetrygdInnlesing): Boolean {
        val burdeVærtProsessert = innlesing.forespurtTidspunkt < now().minus(2.hours.toJavaDuration())
        val antallFerdigeMottakere = mottakerRepo.finnAntallMottakereMedStatusForInnlesing(KortStatus.FERDIG,innlesing.id)
        println("Antall ferdige: $antallFerdigeMottakere")
        // TODO: imlementer
        return false;
    }

}

sealed class ApplicationStatus {
    object OK : ApplicationStatus()
    object IkkeKjort : ApplicationStatus()
    class Feil(val feil: String) : ApplicationStatus()
}