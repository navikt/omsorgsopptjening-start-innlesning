package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.tasks

import io.getunleash.Unleash
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdmottakerService
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdmottakerRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.unleash.NavUnleashConfig
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
            val time1 = System.currentTimeMillis()
            val låste = service.låsForBehandling()
            val time2 = System.currentTimeMillis()
            val antallBarnetrygdmottakere = låste.sumOf { it.data.size }
            log.info("Schedulerer prosessering av $antallBarnetrygdmottakere barnetrygdmottakere")
            val time3 = System.currentTimeMillis()
            schedulerProsessering(låste)
            val time4 = System.currentTimeMillis()
            log.info("processAllAvailableBarnetrygdMottakere:time: låste.size=${låste.size} lås=${time2 - time1}")
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
                val time1 = System.currentTimeMillis()
                val mottakere = service.prosesserOgFrigi(it)
                val time2 = System.currentTimeMillis()
                log.info("taskExecutor.execute: service.prosesserOgFrigi: time=${time2 - time1}")
                mottakere
            }
        }
    }

    private fun isEnabled(): Boolean {
        return unleash.isEnabled(NavUnleashConfig.Feature.PROSESSER_BARNETRYGDMOTTAKER.toggleName)
    }
}