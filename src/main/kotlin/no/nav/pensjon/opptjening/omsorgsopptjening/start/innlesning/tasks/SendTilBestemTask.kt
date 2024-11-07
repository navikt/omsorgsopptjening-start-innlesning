package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.tasks

import io.getunleash.Unleash
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Barnetrygdinformasjon
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.SendTilBestemService
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.config.UnleashConfig
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.metrics.Metrikker
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

class SendTilBestemTask(
    private val service: SendTilBestemService,
    private val metrikker: Metrikker,
    private val unleash: Unleash,
) : Runnable {

    companion object {
        val log = LoggerFactory.getLogger(SendTilBestemTask::class.java)!!
    }

    @Scheduled(fixedDelay = 20_000)
    override fun run() {
        log.info("sendTilBestemTask().run()") // TODO: fjern ekstra logging
        try {
            if (isEnabled()) {
                sendAltSomErKlartTilBestem()
            }
        } catch (ex: Throwable) {
            log.error("Exception caught while processing, type: ${ex::class.qualifiedName}")
            log.error("Pausing for 5 seconds")
            Thread.sleep(5_000)
        }
    }

    private fun sendAltSomErKlartTilBestem() {
        log.info("Sender all tilgjengelig barnetrygdinformasjon til bestem (via kafka)")
        do {
            val prosessert: List<Barnetrygdinformasjon>? = service.sendTilBestem()
            if (!prosessert.isNullOrEmpty()) {
                metrikker.tellSendtTilBestem(prosessert.size)
                log.info("sendt ${prosessert.size} barnetrygdmottakere til bestem")
            }
        } while (!prosessert.isNullOrEmpty())
    }

    private fun isEnabled(): Boolean {
        return unleash.isEnabled(UnleashConfig.Feature.SEND_TIL_BESTEM.toggleName)
    }
}