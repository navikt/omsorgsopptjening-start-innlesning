package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.config

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.monitorering.StatusRapporteringCachingAdapter
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.monitorering.StatusCheckTask
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableScheduling
// @Profile("dev-gcp", "prod-gcp")
class StatusConfig {

    @Autowired
    private lateinit var statusRapporteringsService: StatusRapporteringCachingAdapter
    @Bean
    fun statusCheckTask() : StatusCheckTask {
        return StatusCheckTask(statusRapporteringsService)
    }
}