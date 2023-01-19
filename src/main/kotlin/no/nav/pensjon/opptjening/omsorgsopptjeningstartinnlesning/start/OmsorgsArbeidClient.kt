package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.start

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient

@Component
class OmsorgsArbeidClient(val webClient: WebClient, @Value("\${OMSORGS_ARBEID_URL}") val omsorgsUrl: String) {

    fun startInnlesning(ar: String, hash: String) =
        webClient
            .post()
            .uri("$omsorgsUrl/innlesing/start/")
            .body(BodyInserters.fromValue(RequestBody(ar, hash)))
            .retrieve()
            .getBody()
}

private data class RequestBody(val ar: String, val hash: String)

fun WebClient.ResponseSpec.getBody() = bodyToMono(String::class.java).block()