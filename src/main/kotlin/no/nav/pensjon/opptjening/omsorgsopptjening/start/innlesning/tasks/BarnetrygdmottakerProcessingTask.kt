package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.tasks

import io.getunleash.Unleash
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdmottakerService
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.config.UnleashConfig
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.metrics.Metrikker
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

class BarnetrygdmottakerProcessingTask(
    private val taskExecutor: ThreadPoolTaskExecutor,
    private val service: BarnetrygdmottakerService,
    private val metrikker: Metrikker,
    private val unleash: Unleash,
) : Runnable {
    companion object {
        val log = LoggerFactory.getLogger(BarnetrygdmottakerProcessingTask::class.java)!!
    }

    @Scheduled(fixedDelay = 5000)
    override fun run() {
        if (isEnabled() && isIdle()) {
            log.info("Starter ${taskExecutor.poolSize} antall tasks")
            for (i in 1..taskExecutor.poolSize) {
                taskExecutor.execute {
                    try {
                        processAllAvailableBarnetrygdMottakere()
                    } catch (ex: Throwable) {
                        log.error("Exception caught while processing, type: ${ex::class.qualifiedName}")
                        log.error("Pausing for 10 seconds")
                        Thread.sleep(10_000)
                    }
                }
            }
        }
    }

    private fun processAllAvailableBarnetrygdMottakere() {
        log.info("Prosesserer alle tilgjengelige barnetrygdmottakere")
        var prosesserteMinstEnBarnetrygdmottaker = true
        while (prosesserteMinstEnBarnetrygdmottaker) {
            val barnetrygdmottakere = metrikker.tellBarnetrygdmottakerStatus {
                service.process()
            }
            prosesserteMinstEnBarnetrygdmottaker = !barnetrygdmottakere.isNullOrEmpty()
            if (prosesserteMinstEnBarnetrygdmottaker) {
                log.info("Prosessert ${barnetrygdmottakere?.size} barnetrygdmottakere")
            }
        }
    }

    private fun isEnabled(): Boolean {
        val enabled = unleash.isEnabled(UnleashConfig.Feature.PROSESSER_BARNETRYGDMOTTAKER.toggleName)
        println("isEnabled() -> $enabled")
        return enabled
    }

    private fun isIdle(): Boolean {
        println("isIdle() -> ${taskExecutor.activeCount} == 0")
        return taskExecutor.activeCount == 0
    }
}