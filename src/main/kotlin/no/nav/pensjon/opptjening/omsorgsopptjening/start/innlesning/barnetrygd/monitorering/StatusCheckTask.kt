package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.monitorering

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled


class StatusCheckTask(private val statusRapporteringsService: StatusRapporteringCachingAdapter) {

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }

    @Scheduled(fixedDelay = 120000, initialDelay = 20000)
    fun check() {
        log.info("Sjekker og oppdaterer status for overvåking")
        val status = statusRapporteringsService.oppdaterRapporterbarStatus()
    }
}