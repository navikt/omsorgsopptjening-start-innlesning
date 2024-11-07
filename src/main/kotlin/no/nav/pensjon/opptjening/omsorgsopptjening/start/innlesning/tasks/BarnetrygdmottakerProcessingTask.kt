package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.tasks

import io.getunleash.Unleash
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdmottakerService
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdmottakerRepository
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
        log.info("BarnetrygdmottakerProcessingTask.run()")
        if (isEnabled()) {
            println("processAllAvailableBarnetrygdMottakere()")
            try {
                processAllAvailableBarnetrygdMottakere()
            } catch (ex: Throwable) {
                log.error("Exception caught while processing, type: ${ex::class.qualifiedName}")
                log.error("Pausing for 10 seconds")
                Thread.sleep(10_000)
            }
        }
    }

    private fun processAllAvailableBarnetrygdMottakere() {
        log.info("Prosesserer alle tilgjengelige barnetrygdmottakere")
        do {
            val låste = service.låsForBehandling()
            val antallBarnetrygdmottakere = låste.sumOf { it.data.size }
            log.info("Schedulerer prosessering av $antallBarnetrygdmottakere barnetrygdmottakere")
            schedulerProsessering(låste)
        } while (antallBarnetrygdmottakere > 0)
    }

    private fun schedulerProsessering(låste: List<BarnetrygdmottakerRepository.Locked>) {
        låste.forEach {
            schedulerProsessering(it)
        }
    }

    private fun schedulerProsessering(it: BarnetrygdmottakerRepository.Locked) {
        // Forutsetter at rejection handler i taskExecutor er CallerRunsPolicy, som medfører at
        // prosesseringen blir gjort synkront her hvis executor'en er full
        taskExecutor.execute {
            metrikker.tellBarnetrygdmottakerStatus {
                log.info("Prosesser og frigi barnetrygdmottakere i tråden ${Thread.currentThread().name}")
                service.prosesserOgFrigi(it)
            }
        }
    }

    private fun isEnabled(): Boolean {
        val enabled = unleash.isEnabled(UnleashConfig.Feature.PROSESSER_BARNETRYGDMOTTAKER.toggleName)
        println("isEnabled() -> $enabled")
        return enabled
    }
}