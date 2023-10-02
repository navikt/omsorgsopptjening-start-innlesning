package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.tasks

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration


class StatusCheckTask {

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }

    @Scheduled(fixedDelay = 120000, initialDelay = 60000)
    fun check() {
        log.info("Sjekker og oppdaterer status for overvåking")
        counter += 1
    }

    private var counter = 0
    fun counter() = counter
}