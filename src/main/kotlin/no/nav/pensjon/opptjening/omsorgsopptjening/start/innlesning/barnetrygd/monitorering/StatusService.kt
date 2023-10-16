package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.monitorering

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdInnlesing
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdInnlesingRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant.now
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

@Service
class StatusService(
    private val repo: BarnetrygdInnlesingRepository,
    ) {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }

    fun checkStatus(): ApplicationStatus {
        val innlesing = repo.finnSisteInnlesing()
        if (innlesing == null) return ApplicationStatus.IkkeKjort
        else if (forGammel(innlesing)) return ApplicationStatus.Feil("For lenge siden siste innlesing")
        else if (ikkeProsessert(innlesing)) return ApplicationStatus.Feil("Innlesing er ikke prosessert")
        return ApplicationStatus.OK
    }

    private fun ikkeProsessert(innlesing: BarnetrygdInnlesing): Boolean {
        val maksProsesseringsTid = now().minus(2.hours.toJavaDuration())
        val burdeVærtProsessert = innlesing.forespurtTidspunkt < maksProsesseringsTid
        val erIkkeProsessert = innlesing !is BarnetrygdInnlesing.Ferdig
        return burdeVærtProsessert || erIkkeProsessert
    }

    private fun forGammel(innlesing: BarnetrygdInnlesing): Boolean {
        val minimumTidspunkt = now().minus(400.days.toJavaDuration())
        return innlesing.forespurtTidspunkt < minimumTidspunkt
    }
}

sealed class ApplicationStatus {
    object OK : ApplicationStatus()
    object IkkeKjort : ApplicationStatus()
    class Feil(val feil: String) : ApplicationStatus()
}