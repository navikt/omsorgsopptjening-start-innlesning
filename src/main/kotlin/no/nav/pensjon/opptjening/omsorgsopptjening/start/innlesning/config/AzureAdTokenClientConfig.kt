package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("dev-gcp", "prod-gcp")
class AzureAdTokenClientConfig(
    @Value("\${AZURE_APP_CLIENT_ID}") val azureAppClientId: String,
    @Value("\${AZURE_APP_CLIENT_SECRET}") val azureAppClientSecret: String,
    @Value("\${AZURE_APP_WELL_KNOWN_URL}") val wellKnownUrl: String,
)