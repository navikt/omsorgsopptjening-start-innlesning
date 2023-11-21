package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import jakarta.annotation.PostConstruct
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.metrics.BarnetrygdmottakerProcessingMetrikker
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("dev-gcp", "prod-gcp", "kafkaIntegrationTest")
class BarnetrygdmottakerProcessingThread(
    private val service: BarnetrygdmottakerService,
    private val barnetrygdmottakerProcessingMetrikker: BarnetrygdmottakerProcessingMetrikker,
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
                barnetrygdmottakerProcessingMetrikker.mål {
                    service.process() ?: run {
                        Thread.sleep(1000)
                        null
                    }
                }
            } catch (ex: Throwable) {
                log.error("Exception caught while processing, message:${ex.message}, cause:${ex.cause}")
                Thread.sleep(1000)
            }
        }
    }
}