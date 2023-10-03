package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.config

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.StatusRapporteringsService
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.tasks.StatusCheckTask
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableScheduling
// @Profile("dev-gcp", "prod-gcp")
class StatusConfig {

    @Autowired
    private lateinit var statusRapporteringsService: StatusRapporteringsService
    @Bean
    fun statusCheckTask() : StatusCheckTask {
        return StatusCheckTask(statusRapporteringsService)
    }
}