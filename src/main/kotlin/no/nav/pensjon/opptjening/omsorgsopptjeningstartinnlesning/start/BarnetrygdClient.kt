package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.start

import org.apache.juli.logging.Log
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.toEntity
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.time.Month
import java.util.function.Predicate

@Component
class BarnetrygdClient(
    private val webClient: WebClient,
    @Value("\${BARNETRYGD_URL}") private val url: String
) {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }

    fun initierSendingAvIdenter(ar: Int): BarnetrygdClientResponse {
        log.info("Initiating sending of barnetrygdmottakere by invoking url:$url")
        return webClient
            .post()
            .uri("$url/api/ekstern/pensjon/hent-barnetrygdmottakere")
            .body(
                BodyInserters.fromValue(
                    InitierBarnetrygdDetaljerRequest(
                        LocalDate.of(ar, Month.JANUARY, 1).toString()
                    )
                )
            )
            .retrieve()
            .onStatus(not200()) { Mono.empty() }
            .toEntity<String>()
            .block()?.let { handleResponse(it) } ?: BarnetrygdClientResponse.Feil(null, null)
    }

    fun hentBarnetrygdDetaljer(ident: String, ar: Int): BarnetrygdClientResponse {
        log.info("Retrieving details for ident:$ident, år:$ar from url: $url")
        return webClient
            .post()
            .uri("$url/api/ekstern/pensjon/hent-barnetrygd")
            .body(
                BodyInserters.fromValue(
                    BarnetrygdDetaljerRequest(
                        ident,
                        LocalDate.of(ar, Month.JANUARY, 1).toString()
                    )
                )
            )
            .retrieve()
            .onStatus(not200()) { Mono.empty() }
            .toEntity<String>()
            .block()?.let { handleResponse(it) } ?: BarnetrygdClientResponse.Feil(null, null)
    }

    private fun not200(): Predicate<HttpStatusCode> = Predicate.not(Predicate.isEqual(HttpStatus.OK))

    private fun handleResponse(it: ResponseEntity<String>) =
        when (it.statusCode) {
            HttpStatus.OK -> {
                BarnetrygdClientResponse.Ok(
                    body = it.body
                )
            }

            else -> {
                log.error("Failed to retrieve data from url:$url, status:${it.statusCode}, message:${it.body}")
                BarnetrygdClientResponse.Feil(
                    status = it.statusCode.value(),
                    body = it.body
                )
            }
        }
}

sealed class BarnetrygdClientResponse {
    data class Ok(val body: String?) : BarnetrygdClientResponse()
    data class Feil(val status: Int?, val body: String?) : BarnetrygdClientResponse()
}

private data class BarnetrygdDetaljerRequest(val ident: String, val fraDato: String)
private data class InitierBarnetrygdDetaljerRequest(val fraDato: String)


fun WebClient.ResponseSpec.getBody() = bodyToMono(String::class.java).block()