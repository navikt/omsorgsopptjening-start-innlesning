package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import pensjon.opptjening.azure.ad.client.TokenProvider

@Configuration
@Profile("dev-gcp", "prod-gcp")
class TokenProviderConfig {
    @Bean("barnetrygdTokenProvider")
    fun barnetrygd(
        @Value("\${BARNETRYGD_API_ID}") url: String,
        azureAdTokenClientConfig: AzureAdTokenClientConfig
    ): TokenProvider {
        return AppSpecificTokenProvider(
            appId = url,
            azureAdConfig = azureAdTokenClientConfig
        )
    }

    @Bean("hjelpestonadTokenProvider")
    fun hjelpestonad(
        @Value("\${HJELPESTONAD_API_ID}") url: String,
        azureAdTokenClientConfig: AzureAdTokenClientConfig
    ): TokenProvider {
        return AppSpecificTokenProvider(
            appId = url,
            azureAdConfig = azureAdTokenClientConfig
        )
    }
}