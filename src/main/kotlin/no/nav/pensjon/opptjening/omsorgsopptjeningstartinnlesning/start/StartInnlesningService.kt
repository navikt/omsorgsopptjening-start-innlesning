package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.start

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class StartInnlesningService(val repository: StartHistorikkRepository) {
    companion object {
        private val logger = LoggerFactory.getLogger(StartInnlesningService::class.java)
    }


    fun startInnlesning(ar: String) {
        val localDateTime = LocalDateTime.now()
        val timestamp = localDateTime.format(DateTimeFormatter.ISO_DATE_TIME)
        logger.info("Starter innlesing for år: $ar, timestamp: $timestamp")

        val startHistorikk = StartHistorikk()
        startHistorikk.kjoringsAr = ar
        startHistorikk.kjoringTimesamp = localDateTime

        repository.save(startHistorikk)
    }
}