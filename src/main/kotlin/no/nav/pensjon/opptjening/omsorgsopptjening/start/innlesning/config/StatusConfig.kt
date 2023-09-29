package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.config

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.tasks.StatusCheckTask
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableScheduling
@Profile("dev-gcp", "prod-gcp")
class StatusConfig {

    @Bean
    fun statusCheckTask() {
        StatusCheckTask()
    }
}