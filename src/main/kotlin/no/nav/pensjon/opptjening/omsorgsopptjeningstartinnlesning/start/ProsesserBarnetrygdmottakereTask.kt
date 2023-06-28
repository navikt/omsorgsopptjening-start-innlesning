package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.start

import jakarta.annotation.PostConstruct
import org.apache.juli.logging.Log
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ProsesserBarnetrygdmottakereTask(
    private val service: InnlesningService
) : Runnable {

    companion object {
        val log = LoggerFactory.getLogger(this::class.java)
    }
    @PostConstruct
    fun init(){
        val name = "prosesser-barnetrygdmottakere-thread"
        log.info("Starting new thread:$name to process barnetrygdmottakere")
        Thread(this, name).start()
    }

    override fun run() {
        while(true){
            service.prosesserBarnetrygdmottakere()
        }
    }
}