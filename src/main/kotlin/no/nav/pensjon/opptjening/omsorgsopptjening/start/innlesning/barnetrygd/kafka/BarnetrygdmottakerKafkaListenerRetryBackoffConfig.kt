package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.kafka

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.util.backoff.BackOff
import org.springframework.util.backoff.FixedBackOff

@Configuration
@Profile("dev-gcp", "prod-gcp")
class BarnetrygdmottakerKafkaListenerRetryBackoffConfig {
    @Bean
    fun backoff(): BackOff {
        return FixedBackOff(3000, 3)
    }
}