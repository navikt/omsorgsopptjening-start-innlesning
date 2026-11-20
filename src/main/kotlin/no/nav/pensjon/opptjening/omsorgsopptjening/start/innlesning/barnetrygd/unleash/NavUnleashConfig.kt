package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.unleash

import io.getunleash.DefaultUnleash
import io.getunleash.Unleash
import io.getunleash.util.UnleashConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.net.InetAddress
import java.time.Clock

@Configuration
@Profile("dev-gcp", "prod-gcp")
class NavUnleashConfig(
    @Value($$"${UNLEASH_SERVER_API_URL}") private val unleash_url: String,
    @Value($$"${UNLEASH_SERVER_API_TOKEN}") private val unleash_api_key: String
) {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    @Bean
    fun unleashConfig(): Unleash {
        return DefaultUnleash(
            UnleashConfig.builder()
                .appName("omsorgsopptjening-bestem-pensjonsopptjening")
                .instanceId(InetAddress.getLocalHost().hostName)
                .unleashAPI("$unleash_url/api")
                .apiKey(unleash_api_key)
                .build(),
        )
    }

    @Bean
    fun unleashWrapper(
        unleash: Unleash
    ): UnleashWrapper {
        return UnleashWrapper(
            unleash = unleash,
            clock = Clock.systemUTC()
        ).also { toggle ->
            Feature.entries.forEach {
                log.info("""Feature ${it.toggleName} is enabled: ${toggle.isEnabled(it)}""")
            }
        }
    }

    enum class Feature(val toggleName: String) {
        OPPRETT_OPPGAVER("omsorgsopptjening-bestem-pensjonsopptjening-opprett-oppgaver"),
        GODSKRIV("omsorgsopptjening-bestem-pensjonsopptjening-godskriv"),
        BEHANDLING("omsorgsopptjening-bestem-pensjonsopptjening-behandling"),
        BREV("omsorgsopptjening-bestem-pensjonsopptjening-brev"),
        PROSESSER_BARNETRYGDMOTTAKER("omsorgsopptjening-start-innlesning-prosesser-barnetrygdmottaker"),
        SEND_TIL_BESTEM("omsorgsopptjening-start-innlesning-send-til-bestem"),
    }
}

