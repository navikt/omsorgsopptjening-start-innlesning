package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Mdc
import org.slf4j.LoggerFactory
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.toEntity
import pensjon.opptjening.azure.ad.client.TokenProvider
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.time.Month
import java.util.function.Predicate

@Component
class BarnetrygdClient(private val tokenProvider: TokenProvider,
    private val webClient: WebClient,
) {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }

    fun initierSendingAvIdenter(
        ar: Int
    ): BarnetrygdClientResponse {
        log.info("Initiating sending of barnetrygdmottakere")
        return webClient
            .post()
            .uri("/api/ekstern/pensjon/hent-barnetrygdmottakere")
            .header(CorrelationId.name, Mdc.getOrCreateCorrelationId())
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getToken())
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
            .block()?.let { handleResponse(it) } ?: BarnetrygdClientResponse.Feil(
            null,
            null
        )
    }

    fun hentBarnetrygdDetaljer(
        ident: String,
        ar: Int
    ): BarnetrygdClientResponse {
        log.info("Retrieving details for ident:$ident, år:$ar")
        return webClient
            .post()
            .uri("/api/ekstern/pensjon/hent-barnetrygd")
            .header(CorrelationId.name, Mdc.getOrCreateCorrelationId())
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getToken())
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
            .block()?.let { handleResponse(it) } ?: BarnetrygdClientResponse.Feil(
            null,
            null
        )
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
                log.error("Failed to retrieve data, status:${it.statusCode}, message:${it.body}")
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