package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.tasks

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdinformasjonRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdmottakerRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

class FrigiLaserTask(
    private val barnetrygdmottakerRepository: BarnetrygdmottakerRepository,
    private val barnetrygdinformasjonRepository: BarnetrygdinformasjonRepository,
) {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(FrigiLaserTask::class.java)
    }

    @Scheduled(cron = "0 * * * * *")
    fun frigiLåserForBarnetrygdmottakere() {
        try {
            val frigitt = barnetrygdmottakerRepository.frigiGamleLåser()
            if (frigitt > 0) {
                log.info("barnetrygdmottakere: Frigjorde $frigitt gamle låser")
            }
        } catch (ex: Throwable) {
            log.error("Feil ved frigiving av gamle låser", ex)
        }
    }

    @Scheduled(cron = "0 * * * * *")
    fun frigiLåserForBarnetrygdinformasjon() {
        try {
            val frigitt = barnetrygdinformasjonRepository.frigiGamleLåser()
            if (frigitt > 0) {
                log.info("Barnetrygdinformasjon: Frigjorde $frigitt gamle låser")
            }
        } catch (ex: Throwable) {
            log.error("Feil ved frigiving av gamle låser", ex)
        }
    }


}