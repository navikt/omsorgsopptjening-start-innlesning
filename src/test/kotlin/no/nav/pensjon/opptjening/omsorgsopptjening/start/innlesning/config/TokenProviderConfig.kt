package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import pensjon.opptjening.azure.ad.client.TokenProvider
import pensjon.opptjening.azure.ad.client.mock.MockTokenProvider

@Configuration
class TokenProviderConfig {
    @Bean("pdlTokenProvider")
    fun pdlTokenProvider(): TokenProvider = MockTokenProvider(MOCK_TOKEN)

    @Bean("barnetrygdTokenProvider")
    fun barnetrygd(): TokenProvider = MockTokenProvider(MOCK_TOKEN)

    @Bean("hjelpestonadTokenProvider")
    fun hjelpestønad(): TokenProvider = MockTokenProvider(MOCK_TOKEN)

    companion object {
        const val MOCK_TOKEN = "test.token.test"
    }
}