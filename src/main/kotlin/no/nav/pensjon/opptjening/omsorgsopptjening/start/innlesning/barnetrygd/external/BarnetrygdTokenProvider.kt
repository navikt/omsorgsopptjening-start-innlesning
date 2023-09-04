package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.config.AzureAdTokenClientConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import pensjon.opptjening.azure.ad.client.AzureAdConfig
import pensjon.opptjening.azure.ad.client.AzureAdTokenProvider
import pensjon.opptjening.azure.ad.client.AzureAdVariableConfig
import pensjon.opptjening.azure.ad.client.TokenProvider

@Component
@Profile("dev-gcp", "prod-gcp")
class BarnetrygdTokenProvider(
    @Value("\${BARNETRYGD_API_ID}") val appId: String,
    azureAdConfig: AzureAdTokenClientConfig
) : TokenProvider {
    private val config: AzureAdConfig = AzureAdVariableConfig(
        azureAppClientId = azureAdConfig.azureAppClientId,
        azureAppClientSecret = azureAdConfig.azureAppClientSecret,
        targetApiId = appId,
        wellKnownUrl = azureAdConfig.wellKnownUrl,
    )
    private val tokenProvider: TokenProvider = AzureAdTokenProvider(config)
    override fun getToken(): String {
        return tokenProvider.getToken()
    }
}