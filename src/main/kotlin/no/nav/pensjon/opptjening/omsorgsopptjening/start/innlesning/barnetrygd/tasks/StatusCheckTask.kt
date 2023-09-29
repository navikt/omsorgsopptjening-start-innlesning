package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.tasks

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled


class StatusCheckTask {

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }

    @Scheduled(fixedDelay = 120000, initialDelay = 30000)
    fun check() {
        log.info("Sjekker og oppdaterer status for overvåking")
    }
}