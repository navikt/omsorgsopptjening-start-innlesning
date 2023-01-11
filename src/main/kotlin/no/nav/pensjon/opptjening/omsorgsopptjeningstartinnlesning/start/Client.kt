package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.start

/*
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient


class OmsorgsArbeidClient() {

    val url = "http://localhost:8080"

    private val webClient = WebClient.builder()
        .baseUrl(url)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build()

    fun startInnlesning () : String{
        return webClient
            .get()
            .uri(url)
            .retrieve()
            .getBody()
    }
}

fun WebClient.ResponseSpec.getBody() = bodyToMono(String::class.java).block()!!

 */

