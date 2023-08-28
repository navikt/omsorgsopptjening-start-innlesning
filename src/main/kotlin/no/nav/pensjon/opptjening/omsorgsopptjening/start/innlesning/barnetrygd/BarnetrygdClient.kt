package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.deserialize
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Mdc
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.toEntity
import pensjon.opptjening.azure.ad.client.TokenProvider
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.time.Month
import java.util.function.Predicate

/**
 * @see https://familie-ba-sak.intern.dev.nav.no/swagger-ui/index.html#/pensjon-controller
 */
@Component
class BarnetrygdClient(
    private val tokenProvider: TokenProvider,
    @Value("\${BARNETRYGD_URL}") private val url: String,
) {
    private val webClient: WebClient = WebClient.builder().baseUrl(url).build()

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }

    /**
     * Signaliserer til barnetrygd-systemet at de skal sende oss identen til alle mottakere av barnetrygd i året [ar]
     * og fremover. Barnetrydmottakerne publiseres til topic [no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Topics.BARNETRYGDMOTTAKER].
     */
    fun hentBarnetrygdmottakere(
        ar: Int
    ): HentBarnetygdmottakereResponse {
        log.info("Initiating sending of barnetrygdmottakere")
        return webClient
            .post()
            .uri("/api/ekstern/pensjon/bestill-personer-med-barnetrygd/$ar")
            .header(CorrelationId.identifier, Mdc.getCorrelationId())
            .header(InnlesingId.identifier, Mdc.getInnlesingId())
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getToken())
            .body(
                BodyInserters.fromValue(
                    HentBarnetrygdmottakerRequest(
                        fraDato = LocalDate.of(ar, Month.JANUARY, 1).toString()
                    )
                )
            )
            .retrieve()
            .onStatus(not200()) { Mono.empty() }
            .toEntity<String>()
            .block()?.let { handleHentBarnetrygdmottakere(it, ar) } ?: HentBarnetygdmottakereResponse.Feil(
            null,
            null
        )
    }

    private fun handleHentBarnetrygdmottakere(it: ResponseEntity<String>, ar: Int): HentBarnetygdmottakereResponse {
        return when (val status = it.statusCode) {
            HttpStatus.ACCEPTED -> {
                HentBarnetygdmottakereResponse.Ok(it.body!!, ar)
            }

            HttpStatus.INTERNAL_SERVER_ERROR -> {
                HentBarnetygdmottakereResponse.Feil(
                    status = status.value(),
                    body = deserialize<List<InternalServerErrorResponse>>(it.body!!).toString()
                )
            }

            else -> {
                HentBarnetygdmottakereResponse.Feil(
                    status = it.statusCode.value(),
                    body = it.body.toString()
                )
            }
        }
    }

    /**
     * Hent detaljer om barnetrygdsaken identifisert av [ident], samt det tidligste året vi ønsker å hente data for,
     * angitt av [ar].
     *
     * @return En liste med barnetrygdsaker som inneholder detaljer om saken tilhørende [ident], i tillegg til detaljer
     * om eventuelle relaterte saker. En relatert sak er en annen person enn [ident] som har/har hatt barnetrygd for en/flere
     * av personene [ident] har/har hatt omsorg for.
     */
    fun hentBarnetrygd(
        ident: String,
        ar: Int
    ): HentBarnetrygdResponse {
        log.info("Retrieving details for ident:$ident, år:$ar")
        return webClient
            .post()
            .uri("/api/ekstern/pensjon/hent-barnetrygd")
            .header(CorrelationId.identifier, Mdc.getCorrelationId())
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getToken())
            .body(
                BodyInserters.fromValue(
                    HentBarnetrygdRequest(
                        ident = ident,
                        fraDato = LocalDate.of(ar, Month.JANUARY, 1).toString()
                    )
                )
            )
            .retrieve()
            .onStatus(not200()) { Mono.empty() }
            .toEntity<String>()
            .block()?.let { handleHentBarnetrygd(it) } ?: HentBarnetrygdResponse.Feil(
            null,
            null
        )
    }

    private fun not200(): Predicate<HttpStatusCode> = Predicate.not(Predicate.isEqual(HttpStatus.OK))

    private fun handleHentBarnetrygd(it: ResponseEntity<String>): HentBarnetrygdResponse {
        return when (val status = it.statusCode) {
            HttpStatus.OK -> {
                when {
                    it.body == null -> {
                        HentBarnetrygdResponse.Feil(
                            status = it.statusCode.value(),
                            body = null
                        )
                    }

                    else -> {
                        deserialize<List<Barnetrygdmelding.Sak>>(it.body!!).let {
                            when {
                                it.isEmpty() -> {
                                    HentBarnetrygdResponse.Feil(
                                        status = status.value(),
                                        body = "Liste med barnetrygdsaker er tom"
                                    )
                                }

                                else -> {
                                    HentBarnetrygdResponse.Ok(
                                        barnetrygdsaker = it
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HttpStatus.INTERNAL_SERVER_ERROR -> {
                HentBarnetrygdResponse.Feil(
                    status = status.value(),
                    body = it.body?.let { deserialize<List<InternalServerErrorResponse>>(it).toString() }
                )
            }

            else -> {
                HentBarnetrygdResponse.Feil(
                    status = it.statusCode.value(),
                    body = it.body.toString()
                )
            }
        }
    }
}

sealed class HentBarnetygdmottakereResponse {
    data class Ok(val requestId: String, val år: Int) : HentBarnetygdmottakereResponse()
    data class Feil(val status: Int?, val body: String?) : HentBarnetygdmottakereResponse()
}

sealed class HentBarnetrygdResponse {
    data class Ok(val barnetrygdsaker: List<Barnetrygdmelding.Sak>) : HentBarnetrygdResponse()
    data class Feil(val status: Int?, val body: String?) : HentBarnetrygdResponse()
}

private data class HentBarnetrygdRequest(val ident: String, val fraDato: String)
private data class HentBarnetrygdmottakerRequest(val fraDato: String)

private data class InternalServerErrorResponse(
    val data: Any? = null,
    val status: String,
    val melding: String,
    val frontendMelding: String? = null,
    val stacktrace: String? = null
)