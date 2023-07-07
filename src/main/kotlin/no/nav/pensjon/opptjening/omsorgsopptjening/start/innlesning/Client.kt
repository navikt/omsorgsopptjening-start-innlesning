package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class Client(@Value("\${BARNETRYGD_URL}") private val url: String) {

    @Bean
    fun webClient(): WebClient = WebClient.builder().baseUrl(url).build()
}