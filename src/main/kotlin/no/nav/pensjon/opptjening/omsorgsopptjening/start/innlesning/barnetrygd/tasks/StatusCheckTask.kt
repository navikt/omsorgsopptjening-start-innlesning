package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.tasks

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.StatusRapporteringsService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled


class StatusCheckTask(private val statusRapporteringsService: StatusRapporteringsService) {

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }

    @Scheduled(fixedDelay = 300 /* 120000 */, initialDelay = 0 /* 60000 */)
    fun check() {
        log.info("Sjekker og oppdaterer status for overvåking")
        val status = statusRapporteringsService.oppdaterRapporterbarStatus()
    }
}