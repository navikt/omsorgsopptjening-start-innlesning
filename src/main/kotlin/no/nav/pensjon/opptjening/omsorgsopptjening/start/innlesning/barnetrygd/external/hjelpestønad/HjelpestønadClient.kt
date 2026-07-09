package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.hjelpestønad

import java.time.LocalDate
import java.time.YearMonth
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.deserializeList
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.RådataFraKilde
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Mdc
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Ident
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.hjelpestønad.Serializer.toJson
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.utf8RestTemplate
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.metrics.Metrikker
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

@Component
class HjelpestønadClient(
    @Qualifier("hjelpestonadTokenProvider") private val tokenProvider: TokenProvider,
    @Value("\${HJELPESTONAD_URL}") private val baseUrl: String,
    internal val metrikker: Metrikker,
    requestFactory: ClientHttpRequestFactory,
) {
    // Kast ikke på non-2xx; body blir mata inn i deserialize-eller-tom-logikken (som WebClient onStatus(not200) + Mono.empty tidligere)
        private val restTemplate: RestTemplate = utf8RestTemplate(requestFactory).apply {
            errorHandler = NoOpResponseErrorHandler()
        }

    internal fun hentHjelpestønad(
        fnr: Ident,
        fom: LocalDate,
        tom: LocalDate
    ): HentHjelpestønadDBResponse {
        return metrikker.målHentHjelpestønad { hentHjelpestønadInternal(fnr, fom, tom) }!!
    }

    private fun hentHjelpestønadInternal(
        fnr: Ident,
        fom: LocalDate,
        tom: LocalDate
    ): HentHjelpestønadDBResponse {
        val response = restTemplate.exchange(
            "$baseUrl/api/hjelpestonad/hent",
            HttpMethod.POST,
            HttpEntity(
                HentHjelpestønadQuery(
                    fnr = fnr.value,
                    fom = fom,
                    tom = tom
                ).toJson(),
                HttpHeaders().apply {
                    set(CorrelationId.identifier, Mdc.getCorrelationId().toString())
                    set(InnlesingId.identifier, Mdc.getInnlesingId().toString())
                    accept = listOf(MediaType.APPLICATION_JSON)
                    contentType = MediaType.APPLICATION_JSON
                    set(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getToken())
                }
            ),
            String::class.java,
        )

        val body = response.body
        return body?.deserializeList<HjelpestønadVedtak>()?.let {
            HentHjelpestønadDBResponse(
                vedtak = it,
                rådataFraKilde = RådataFraKilde(
                    mapOf(
                        "fnr" to fnr.value,
                        "fom" to fom.toString(),
                        "tom" to tom.toString(),
                        "hjelpestønad" to body
                    )
                )
            )
        } ?: HentHjelpestønadDBResponse(
            vedtak = emptyList(),
            rådataFraKilde = RådataFraKilde(
                mapOf(
                    "fnr" to fnr.value,
                    "fom" to fom.toString(),
                    "tom" to tom.toString(),
                    "hjelpestønad" to "$body"
                )
            )
        )
    }

}

data class HentHjelpestønadDBResponse(
    val vedtak: List<HjelpestønadVedtak>,
    val rådataFraKilde: RådataFraKilde
)

data class HjelpestønadVedtak(
    val id: Int,
    val ident: String,
    val fom: YearMonth,
    val tom: YearMonth?,
    val omsorgstype: HjelpestønadType
)

enum class HjelpestønadType {
    FORHØYET_SATS_3,
    FORHØYET_SATS_4;
}
