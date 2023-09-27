package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdInnlesingRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant.now

@Service
class StatusService(
    private val repo: BarnetrygdInnlesingRepository,
    private val transactionTemplate: TransactionTemplate,
) {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }

    fun checkStatus(): ApplicationStatus {
        val innlesing = repo.finnSisteInnlesing()
        if (innlesing == null) return ApplicationStatus.IkkeKjort
        else if (forGammel(innlesing)) return ApplicationStatus.Feil(listOf("For lenge siden siste innlesing"))
        else if (ikkeProsessert(innlesing)) return ApplicationStatus.Feil(listOf("Innlesing er ikke prosessert"))
        return ApplicationStatus.OK
    }

    private fun ikkeProsessert(innlesing: BarnetrygdInnlesing): Boolean {
        val maksProsesseringsTid = now().minus(Duration.ofHours(2))
        val burdeVærtProsessert = innlesing.forespurtTidspunkt < maksProsesseringsTid
        val erIkkeProsessert = innlesing.ferdigTidspunkt != null
        return burdeVærtProsessert || erIkkeProsessert
    }

    private fun forGammel(innlesing: BarnetrygdInnlesing): Boolean {
        val minimumTidspunkt = now().minus(Duration.ofDays(400))
        return innlesing.forespurtTidspunkt < minimumTidspunkt
    }
}

sealed class ApplicationStatus {
    object OK : ApplicationStatus()
    object IkkeKjort : ApplicationStatus()
    class Feil(val feil: List<String>) : ApplicationStatus()
}