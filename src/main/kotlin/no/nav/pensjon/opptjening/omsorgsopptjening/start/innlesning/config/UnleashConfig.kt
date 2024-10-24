package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.config

import io.getunleash.DefaultUnleash
import io.getunleash.Unleash
import io.getunleash.strategy.DefaultStrategy
import io.getunleash.util.UnleashConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.net.InetAddress

@Configuration
@Profile("dev-gcp", "prod-gcp")
class UnleashConfig(
    @Value("\${UNLEASH_SERVER_API_URL}") private val unleash_url: String,
    @Value("\${UNLEASH_SERVER_API_TOKEN}") private val unleash_api_key: String
) {

    @Bean("unleash")
    fun unleashConfig(): Unleash {
        return DefaultUnleash(
            UnleashConfig.builder()
                .appName("omsorgsopptjening-start-innlesning")
                .instanceId(InetAddress.getLocalHost().hostName)
                .unleashAPI("$unleash_url/api")
                .apiKey(unleash_api_key)
                .build(),
            DefaultStrategy()
        )
    }

    enum class Feature(val toggleName: String) {
        PROSESSER_BARNETRYGDMOTTAKER("omsorgsopptjening-start-innlesning-prosesser-barnetrygdmottaker"),
        SEND_TIL_BESTEM("omsorgsopptjening-start-innlesning-send-til-bestem"),
    }
}

