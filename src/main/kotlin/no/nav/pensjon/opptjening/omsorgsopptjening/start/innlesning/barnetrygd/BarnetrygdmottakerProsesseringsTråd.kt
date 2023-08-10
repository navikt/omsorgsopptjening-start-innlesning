package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("dev-gcp", "prod-gcp", "kafkaIntegrationTest")
class BarnetrygdmottakerProsesseringsTråd(
    private val service: BarnetrygdService
) : Runnable {

    companion object {
        val log = LoggerFactory.getLogger(this::class.java)
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
                service.prosesserBarnetrygdmottakere()
            } catch (ex: Throwable) {
                log.error("Exception caught while processing, message:${ex.message}, cause:${ex.cause}")
                Thread.sleep(1000)
            }
        }
    }
}