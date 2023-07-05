package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning

import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class Client() {

    @Bean
    fun webClient(): WebClient = WebClient.builder().build()
}