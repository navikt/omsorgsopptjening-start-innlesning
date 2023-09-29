package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.config

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.tasks.StatusCheckTask
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableScheduling
class StatusConfig {

    @Bean
    fun statusCheckTask() {
        StatusCheckTask()
    }
}