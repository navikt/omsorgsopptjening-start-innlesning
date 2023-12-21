package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.tasks

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdmottakerRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

class FrigiLaserTask(
    val barnetrygdmottakerRepo: BarnetrygdmottakerRepository,
) {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    @Scheduled(cron = "*/15 * * * * *")
    fun run() {
        try {
            log.info("Frigir gamle låser")
            barnetrygdmottakerRepo.frigiGamleLåser()
        } catch (ex: Throwable) {
            log.error("Feil ved frigiving av gamle låser", ex)
        }
    }
}