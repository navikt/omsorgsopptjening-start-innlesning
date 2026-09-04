package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.config

import io.getunleash.FakeUnleash
import io.getunleash.Unleash
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class UnleashConfig {
    @Bean("unleash")
    fun unleashConfig(): Unleash {
        return FakeUnleash().also { it.enableAll() }
    }
}
