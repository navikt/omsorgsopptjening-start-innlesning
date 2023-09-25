package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdInnlesingRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

@Service
class StatusService(
    private val repo: BarnetrygdInnlesingRepository,
    private val transactionTemplate: TransactionTemplate,
) {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }

    fun checkStatus(): ApplicationStatus {
        if (repo.finnSisteInnlesing() == null) return ApplicationStatus.IkkeKjort
        return ApplicationStatus.Feil(listOf("a", "b"))
    }
}

sealed class ApplicationStatus {
    object OK : ApplicationStatus()
    object IkkeKjort : ApplicationStatus()
    class Feil(val feil: List<String>) : ApplicationStatus()
}