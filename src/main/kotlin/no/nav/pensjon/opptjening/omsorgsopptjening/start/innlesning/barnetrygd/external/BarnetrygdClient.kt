package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Mdc
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.toEntity
import pensjon.opptjening.azure.ad.client.TokenProvider
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.time.Month
import java.util.UUID
import java.util.function.Predicate

/**
 * @see https://familie-ba-sak.intern.dev.nav.no/swagger-ui/index.html#/pensjon-controller
 */
@Component
class BarnetrygdClient(
    @Qualifier("barnetrygdTokenProvider") private val tokenProvider: TokenProvider,
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
    fun bestillBarnetrygdmottakere(
        ar: Int
    ): BestillBarnetrygdmottakereResponse {
        log.info("Initiating sending of barnetrygdmottakere")
        return webClient
            .get()
            .uri("/api/ekstern/pensjon/bestill-personer-med-barnetrygd/$ar")
            .header(CorrelationId.identifier, UUID.randomUUID().toString())
            .header(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN_VALUE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getToken())
            .retrieve()
            .onStatus(not202()) { Mono.empty() }
            .toEntity<String>()
            .block()?.let { BestillBarnetrygdResponseHandler.handle(it, ar) }
            ?: throw BestillBarnetrygdMottakereException("Response var null")
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
            .header(CorrelationId.identifier, Mdc.getCorrelationId().toString())
            .header(InnlesingId.identifier, Mdc.getInnlesingId().toString())
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
            .block()?.let { HentBarnetrygdResponseHandler.handle(it,ar) }
            ?: throw HentBarnetrygdException("Response var null")
    }

    data class HentBarnetrygdRequest(val ident: String, val fraDato: String)

    private fun not200(): Predicate<HttpStatusCode> = Predicate.not(Predicate.isEqual(HttpStatus.OK))
    private fun not202(): Predicate<HttpStatusCode> = Predicate.not(Predicate.isEqual(HttpStatus.ACCEPTED))

}