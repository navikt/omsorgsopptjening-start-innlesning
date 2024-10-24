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
            val prosessert: List<Barnetrygdinformasjon>? = service.process()
            if (prosessert?.isNotEmpty() == true) {
                metrikker.tellSendtTilBestem(prosessert.size)
            }
            val harGjortNoe = prosessert.isNullOrEmpty()
            if (harGjortNoe) {
                log.info("Prosessert ${prosessert?.size} barnetrygdmottakere")
            }
        }
    }

    private fun isEnabled(): Boolean {
        return unleash.isEnabled(UnleashConfig.Feature.SEND_TIL_BESTEM.toggleName)
    }
}