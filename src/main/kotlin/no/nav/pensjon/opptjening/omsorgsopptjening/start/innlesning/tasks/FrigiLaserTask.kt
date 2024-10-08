package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.tasks

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdmottakerRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

class FrigiLaserTask(
    val barnetrygdmottakerRepo: BarnetrygdmottakerRepository,
) {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(FrigiLaserTask::class.java)
    }

    @Scheduled(cron = "0 * * * * *")
    fun run() {
        try {
            val frigitt = barnetrygdmottakerRepo.frigiGamleLåser()
            if (frigitt > 0) {
                log.info("Frigjorde $frigitt gamle låser")
            }
        } catch (ex: Throwable) {
            log.error("Feil ved frigiving av gamle låser", ex)
        }
    }
}