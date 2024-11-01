package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.barnetrygd

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Mdc
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.GyldigÅrsintervallFilter
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.HentBarnetrygdResponse
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.År
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.metrics.Metrikker
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
import java.util.UUID
import java.util.function.Predicate

/**
 * @see https://familie-ba-sak.intern.dev.nav.no/swagger-ui/index.html#/pensjon-controller
 */
@Component
class BarnetrygdClient(
    @Qualifier("barnetrygdTokenProvider") private val tokenProvider: TokenProvider,
    @Value("\${BARNETRYGD_URL}") private val url: String,
    private val metrikker: Metrikker,
    webClientBuilder: WebClient.Builder,
) {
    private val webClient: WebClient = webClientBuilder.baseUrl(url).build()

    companion object {
        private val log = LoggerFactory.getLogger(BarnetrygdClient::class.java)
    }

    /**
     * Signaliserer til barnetrygd-systemet at de skal sende oss identen til alle mottakere av barnetrygd i året [ar]
     * og fremover. Barnetrydmottakerne publiseres til topic \$BARNETRYGDMOTTAKERE_TOPIC.
     */
    fun bestillBarnetrygdmottakere(
        ar: År
    ): BestillBarnetrygdmottakereResponse {
        return webClient
            .get()
            .uri("/api/ekstern/pensjon/bestill-personer-med-barnetrygd/${ar.value}")
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
     * angitt av [gyldigÅrsintervall].
     *
     * @return En liste med barnetrygdsaker som inneholder detaljer om saken tilhørende [ident], i tillegg til detaljer
     * om eventuelle relaterte saker. En relatert sak er en annen person enn [ident] som har/har hatt barnetrygd for en/flere
     * av personene [ident] har/har hatt omsorg for.
     */

    fun hentBarnetrygd(
        ident: String,
        gyldigÅrsintervall: GyldigÅrsintervallFilter
    ): HentBarnetrygdResponse {
        return metrikker.målHentBarnetrygd { hentBarnetrygdInternal(ident, gyldigÅrsintervall) }!!
    }

    fun hentBarnetrygdInternal(
        ident: String,
        filter: GyldigÅrsintervallFilter
    ): HentBarnetrygdResponse {
        val request = HentBarnetrygdRequest(
            ident = ident,
            fraDato = filter.minDato().toString()
        )
        return webClient
            .post()
            .uri("/api/ekstern/pensjon/hent-barnetrygd")
            .header(CorrelationId.identifier, Mdc.getCorrelationId().toString())
            .header(InnlesingId.identifier, Mdc.getInnlesingId().toString())
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getToken())
            .body(BodyInserters.fromValue(request))
            .retrieve()
            .onStatus(not200()) { Mono.empty() }
            .toEntity<String>()
            .block()?.let {
                HentBarnetrygdResponseHandler.handle(
                    request = request,
                    response = it,
                    filter = filter
                )
            }
            ?: throw HentBarnetrygdException("Response var null")
    }

    data class HentBarnetrygdRequest(val ident: String, val fraDato: String)

    private fun not200(): Predicate<HttpStatusCode> = Predicate.not(Predicate.isEqual(HttpStatus.OK))
    private fun not202(): Predicate<HttpStatusCode> = Predicate.not(Predicate.isEqual(HttpStatus.ACCEPTED))

}