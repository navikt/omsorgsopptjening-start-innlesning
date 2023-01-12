package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.start

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class OmsorgsArbeidClient(val webClient: WebClient, @Value("\${OMSORGS_ARBEID_URL}") val omsorgsUrl: String) {

    fun startInnlesning () =
        webClient
            .get()
            .uri("$omsorgsUrl/start")
            .retrieve()
            .getBody()
}

fun WebClient.ResponseSpec.getBody() = bodyToMono(String::class.java).block()


