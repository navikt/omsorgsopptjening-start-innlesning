package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import io.getunleash.Unleash
import jakarta.annotation.PostConstruct
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.metrics.BarnetrygdmottakerProcessingMetrikker
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.config.UnleashConfig
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("dev-gcp", "prod-gcp", /* "kafkaIntegrationTest" */)
class BarnetrygdmottakerProcessingThread(
    private val service: BarnetrygdmottakerService,
    private val barnetrygdmottakerProcessingMetrikker: BarnetrygdmottakerProcessingMetrikker,
    private val unleash: Unleash,
) : Runnable {

    companion object {
        val log = LoggerFactory.getLogger(BarnetrygdmottakerProcessingThread::class.java)!!
    }

    @PostConstruct
    fun init() {
        val name = "prosesser-barnetrygdmottakere-thread"
        log.info("Starting new thread:$name to process barnetrygdmottakere")
        Thread(this, name).start()
    }

    override fun run() {
        while (true) {
            try {
                if (unleash.isEnabled(UnleashConfig.Feature.PROSESSER_BARNETRYGDMOTTAKER.toggleName)) {
                    barnetrygdmottakerProcessingMetrikker.mål {
                        service.process()?.let { null } ?: run {
                            Thread.sleep(1000)
                            null
                        }
                    }
                }
            } catch (ex: Throwable) {
                log.error("Exception caught while processing, type: ${ex::class.qualifiedName}")
                Thread.sleep(10_000)
            }
        }
    }
}