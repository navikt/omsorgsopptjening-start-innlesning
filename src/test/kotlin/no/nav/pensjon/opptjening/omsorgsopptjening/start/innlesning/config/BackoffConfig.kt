package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.util.backoff.BackOff
import org.springframework.util.backoff.FixedBackOff

@Configuration
class BackoffConfig {
    @Bean
    fun backoff(): BackOff {
        return FixedBackOff(1000, 2)
    }
}
