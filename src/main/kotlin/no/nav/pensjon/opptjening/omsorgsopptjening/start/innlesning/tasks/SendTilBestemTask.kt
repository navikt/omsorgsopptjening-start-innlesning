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

    @Scheduled(fixedDelay = 5000)
    override fun run() {
        log.info("sendTilBestemTask().run()") // TODO: fjern ekstra logging
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
        var merÅGjøre = true
        while (merÅGjøre) {
            val prosessert: List<Barnetrygdinformasjon>? = service.process()
            if (prosessert?.isNotEmpty() == true) {
                metrikker.tellSendtTilBestem(prosessert.size)
            }
            merÅGjøre = prosessert.isNullOrEmpty()
            if (merÅGjøre) {
                log.info("Prosessert ${prosessert?.size} barnetrygdmottakere")
            }
        }
    }

    private fun isEnabled(): Boolean {
        return unleash.isEnabled(UnleashConfig.Feature.SEND_TIL_BESTEM.toggleName)
    }
}