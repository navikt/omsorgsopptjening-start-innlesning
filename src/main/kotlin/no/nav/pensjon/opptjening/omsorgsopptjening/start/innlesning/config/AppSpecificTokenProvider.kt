package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.config

import pensjon.opptjening.azure.ad.client.AzureAdConfig
import pensjon.opptjening.azure.ad.client.AzureAdTokenProvider
import pensjon.opptjening.azure.ad.client.AzureAdVariableConfig
import pensjon.opptjening.azure.ad.client.TokenProvider

class AppSpecificTokenProvider(
    appId: String,
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