package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.config

import io.getunleash.Unleash
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdmottakerService
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.SendTilBestemService
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.monitorering.StatusCheckTask
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.monitorering.StatusRapporteringCachingAdapter
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdinformasjonRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdmottakerRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.metrics.Metrikker
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.tasks.BarnetrygdmottakerProcessingTask
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.tasks.FrigiLaserTask
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.tasks.SendTilBestemTask
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.ThreadPoolExecutor

@Configuration
@EnableScheduling
@Profile("dev-gcp", "prod-gcp")
class ScheduledTasksConfig(
    private val barnetrygdmottakerRepository: BarnetrygdmottakerRepository,
    private val statusRapporteringCachingAdapter: StatusRapporteringCachingAdapter,
    private val barnetrygdmottakerService: BarnetrygdmottakerService,
    private val metrikker: Metrikker,
    private val unleash: Unleash,
) {
    @Bean
    fun statusCheckTask(): StatusCheckTask {
        return StatusCheckTask(statusRapporteringCachingAdapter)
    }

    @Bean
    fun frigiGamleLåserTask(barnetrygdinformasjonRepository: BarnetrygdinformasjonRepository): FrigiLaserTask {
        return FrigiLaserTask(
            barnetrygdmottakerRepository = barnetrygdmottakerRepository,
            barnetrygdinformasjonRepository = barnetrygdinformasjonRepository,
        )
    }

    @Bean
    fun barnetrygdmottakerProcessingTask(
        @Qualifier("scheduledTasksExecutor") taskExecutor: ThreadPoolTaskExecutor,
    ): BarnetrygdmottakerProcessingTask {
        return BarnetrygdmottakerProcessingTask(
            taskExecutor = taskExecutor,
            service = barnetrygdmottakerService,
            metrikker = metrikker,
            unleash = unleash,
        )
    }

    @Bean
    fun sendToBestemTask(sendTilBestemService: SendTilBestemService): SendTilBestemTask {
        return SendTilBestemTask(
            service = sendTilBestemService,
            metrikker = metrikker,
            unleash = unleash,
        )
    }

    @Bean("scheduledTasksExecutor")
    fun threadpoolExecutor(): ThreadPoolTaskExecutor {
        return ThreadPoolTaskExecutor().apply {
            queueCapacity = 25
            corePoolSize = 1
            maxPoolSize = 5
            setThreadNamePrefix("ScheduledTasksExecutor-")
            setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
            setAllowCoreThreadTimeOut(true)
            keepAliveSeconds = 60
            initialize()
        }
    }
}