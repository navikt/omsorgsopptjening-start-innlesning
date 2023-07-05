package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import pensjon.opptjening.azure.ad.client.TokenProvider

@Configuration
class TokenProfiderConfig {
    @Bean
    fun tokenProviderTest(): TokenProvider = object: TokenProvider {
        override fun getToken(): String {
            return "bananas"
        }
    }
}