package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.config

import io.getunleash.Unleash
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdmottakerService
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.metrics.BarnetrygdmottakerProcessingMetrikker
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.monitorering.StatusCheckTask
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.monitorering.StatusRapporteringCachingAdapter
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdmottakerRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.tasks.BarnetrygdmottakerProcessingTask
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.tasks.FrigiLaserTask
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableScheduling
@Profile("dev-gcp", "prod-gcp")
class ScheduledTasksConfig(
    private val barnetrygdmottakerRepository: BarnetrygdmottakerRepository,
    private val statusRapporteringCachingAdapter: StatusRapporteringCachingAdapter,
    private val barnetrygdmottakerService: BarnetrygdmottakerService,
    private val barnetrygdmottakerProcessingMetrikker: BarnetrygdmottakerProcessingMetrikker,
    private val unleash: Unleash,
) {
    @Bean
    fun statusCheckTask(): StatusCheckTask {
        return StatusCheckTask(statusRapporteringCachingAdapter)
    }

    @Bean
    fun frigiGamleLåserTask(): FrigiLaserTask {
        return FrigiLaserTask(barnetrygdmottakerRepository)
    }

    @Bean
    fun barnetrygdmottakerProcessingTask(): BarnetrygdmottakerProcessingTask {
        return BarnetrygdmottakerProcessingTask(
            service = barnetrygdmottakerService,
            barnetrygdmottakerProcessingMetrikker = barnetrygdmottakerProcessingMetrikker,
            unleash = unleash,
        )
    }
}