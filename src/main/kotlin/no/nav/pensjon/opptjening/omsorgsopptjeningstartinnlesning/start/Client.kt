package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.start


import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class OmsorgsArbeidClient(@Value("\${OMSORGS_ARBEID_URL}") val omsorgsUrl: String) {

    private val webClient = WebClient.builder()
        .baseUrl(omsorgsUrl)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build()

    fun startInnlesning () : String?{
        return webClient
            .get()
            .uri("$omsorgsUrl/start")
            .retrieve()
            .getBody()
    }
}

fun WebClient.ResponseSpec.getBody() = bodyToMono(String::class.java).block()


