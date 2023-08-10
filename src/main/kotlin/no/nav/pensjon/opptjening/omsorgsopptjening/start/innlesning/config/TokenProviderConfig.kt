package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import pensjon.opptjening.azure.ad.client.AzureAdTokenProvider
import pensjon.opptjening.azure.ad.client.AzureAdVariableConfig
import pensjon.opptjening.azure.ad.client.TokenProvider

@Configuration
@Profile("dev-gcp", "prod-gcp")
class TokenProviderConfig {

    @Bean
    fun azureAdConfig(
        @Value("\${AZURE_APP_CLIENT_ID}") azureAppClientId: String,
        @Value("\${AZURE_APP_CLIENT_SECRET}") azureAppClientSecret: String,
        @Value("dev-gcp.pensjonopptjening.omsorgsarbeid-ba-mock") scope: String,
        @Value("\${AZURE_APP_WELL_KNOWN_URL}") wellKnownUrl: String,
    ) = AzureAdVariableConfig(
        azureAppClientId = azureAppClientId,
        azureAppClientSecret = azureAppClientSecret,
        targetApiId = scope,
        wellKnownUrl = wellKnownUrl,
    )


    @Bean
    fun tokenProvider(azureAdVariableConfig: AzureAdVariableConfig): TokenProvider =
        AzureAdTokenProvider(azureAdVariableConfig)
}