package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.barnetrygd

import java.util.UUID
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Mdc
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.GyldigÅrsintervallFilter
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.HentBarnetrygdResponse
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.År
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.utf8RestTemplate
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.metrics.Metrikker
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.NoOpResponseErrorHandler
import org.springframework.web.client.RestTemplate
import pensjon.opptjening.azure.ad.client.TokenProvider

/**
 * See https://familie-ba-sak.intern.dev.nav.no/swagger-ui/index.html#/pensjon-controller
 */
@Component
class BarnetrygdClient(
    @Qualifier("barnetrygdTokenProvider") private val tokenProvider: TokenProvider,
    @Value("\${BARNETRYGD_URL}") private val url: String,
    private val metrikker: Metrikker,
    requestFactory: ClientHttpRequestFactory,
) {
    // Kast ikke på non-2xx; ResponseHandler avgjør utfallet (som WebClient onStatus + Mono.empty tidligere)
    private val restTemplate: RestTemplate = utf8RestTemplate(requestFactory).apply {
        errorHandler = NoOpResponseErrorHandler()
    }

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
        val response = restTemplate.exchange(
            "$url/api/ekstern/pensjon/bestill-personer-med-barnetrygd/${ar.value}",
            HttpMethod.GET,
            HttpEntity<Void>(HttpHeaders().apply {
                set(CorrelationId.identifier, UUID.randomUUID().toString())
                accept = listOf(MediaType.TEXT_PLAIN)
                set(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getToken())
            }),
            String::class.java,
        )
        return BestillBarnetrygdResponseHandler.handle(response, ar)
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
        val response = restTemplate.exchange(
            "$url/api/ekstern/pensjon/hent-barnetrygd",
            HttpMethod.POST,
            HttpEntity(request, HttpHeaders().apply {
                set(CorrelationId.identifier, Mdc.getCorrelationId().toString())
                set(InnlesingId.identifier, Mdc.getInnlesingId().toString())
                contentType = MediaType.APPLICATION_JSON
                accept = listOf(MediaType.APPLICATION_JSON)
                set(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getToken())
            }),
            String::class.java,
        )
        return HentBarnetrygdResponseHandler.handle(
            request = request,
            response = response,
            filter = filter
        )
    }

    data class HentBarnetrygdRequest(val ident: String, val fraDato: String)

}
