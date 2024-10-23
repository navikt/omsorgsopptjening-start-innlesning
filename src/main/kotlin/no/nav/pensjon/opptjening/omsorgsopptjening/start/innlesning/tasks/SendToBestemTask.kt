package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.tasks

import io.getunleash.Unleash
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdmottakerService
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.config.UnleashConfig
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.metrics.Metrikker
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

class SendToBestemTask(
    private val service: BarnetrygdmottakerService,
    private val metrikker: Metrikker,
    private val unleash: Unleash,
) : Runnable {

    companion object {
        val log = LoggerFactory.getLogger(SendToBestemTask::class.java)!!
    }

    @Scheduled(fixedDelay = 1000)
    override fun run() {
        try {
            if (isEnabled()) {
                sendAltSomErKlartTilBestem()
            }
        } catch (ex: Throwable) {
            log.error("Exception caught while processing, type: ${ex::class.qualifiedName}")
            log.error("Pausing for 10 seconds")
            Thread.sleep(10_000)
        }
    }

    private fun sendAltSomErKlartTilBestem() {
        log.info("Sender all tilgjengelig barnetrygdinformasjon til bestem (via kafka)")
        var harGjortNoe = true
        while (harGjortNoe) {
            val barnetrygdmottakere = metrikker.tellBarnetrygdmottakerStatus {
                service.process()
            }
            harGjortNoe = !barnetrygdmottakere.isNullOrEmpty()
            if (harGjortNoe) {
                log.info("Prosessert ${barnetrygdmottakere?.size} barnetrygdmottakere")
            }
        }
    }

    private fun isEnabled(): Boolean {
        return unleash.isEnabled(UnleashConfig.Feature.PROSESSER_BARNETRYGDMOTTAKER.toggleName)
    }
}