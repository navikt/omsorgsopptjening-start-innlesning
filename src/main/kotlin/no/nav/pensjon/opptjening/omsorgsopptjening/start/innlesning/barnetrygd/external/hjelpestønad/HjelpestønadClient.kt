package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.hjelpestønad

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.deserializeList
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.RådataFraKilde
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Mdc
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.metrics.Metrikker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.toEntity
import pensjon.opptjening.azure.ad.client.TokenProvider
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.time.YearMonth
import java.util.function.Predicate

@Component
class HjelpestønadClient(
    @Qualifier("hjelpestonadTokenProvider") private val tokenProvider: TokenProvider,
    @Value("\${HJELPESTONAD_URL}") private val baseUrl: String,
    internal val metrikker: Metrikker,
    webClientBuilder: WebClient.Builder,
) {
    private val webClient: WebClient = webClientBuilder.baseUrl(baseUrl).build()

    internal fun hentHjelpestønad(
        fnr: String,
        fom: LocalDate,
        tom: LocalDate
    ): HentHjelpestønadDBResponse {
        return metrikker.målHentHjelpestønad { hentHjelpestønadInternal(fnr, fom, tom) }!!
    }

    private fun hentHjelpestønadInternal(
        fnr: String,
        fom: LocalDate,
        tom: LocalDate
    ): HentHjelpestønadDBResponse {
        return webClient
            .get()
            .uri("/api/hjelpestonad?fom=$fom&tom=$tom")
            .header("fnr", fnr)
            .header(CorrelationId.identifier, Mdc.getCorrelationId().toString())
            .header(InnlesingId.identifier, Mdc.getInnlesingId().toString())
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getToken())
            .retrieve()
            .onStatus(not200()) { Mono.empty() }
            .toEntity<String>()
            .block()?.let { response ->
                response.body?.deserializeList<HjelpestønadVedtak>()?.let {
                    HentHjelpestønadDBResponse(
                        vedtak = it,
                        rådataFraKilde = RådataFraKilde(
                            mapOf(
                                "fnr" to fnr,
                                "fom" to fom.toString(),
                                "tom" to tom.toString(),
                                "hjelpestønad" to "${response.body}"
                            )
                        )
                    )
                } ?: HentHjelpestønadDBResponse(
                    vedtak = emptyList(),
                    rådataFraKilde = RådataFraKilde(
                        mapOf(
                            "fnr" to fnr,
                            "fom" to fom.toString(),
                            "tom" to tom.toString(),
                            "hjelpestønad" to "${response.body}"
                        )
                    )
                )
            }
            ?: throw HentHjelpestønadException("Response var null")
    }

    private fun not200(): Predicate<HttpStatusCode> = Predicate.not(Predicate.isEqual(HttpStatus.OK))

}

data class HentHjelpestønadException(val msg: String) : RuntimeException(msg)

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