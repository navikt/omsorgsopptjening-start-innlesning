package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.hjelpestønad.external

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.deserializeList
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.RådataFraKilde
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Mdc
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
) {
    private val webClient: WebClient = WebClient.builder().baseUrl(baseUrl).build()

    internal fun hentHjelpestønad(
        fnr: String,
        fom: LocalDate,
        tom: LocalDate
    ): HentHjelpestønadResponse {
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
                    HentHjelpestønadResponse(
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
                } ?: HentHjelpestønadResponse(
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

internal data class HentHjelpestønadResponse(
    val vedtak: List<HjelpestønadVedtak>,
    val rådataFraKilde: RådataFraKilde
)

internal data class HjelpestønadVedtak(
    val id: Int,
    val ident: String,
    val fom: YearMonth,
    val tom: YearMonth?,
    val omsorgstype: HjelpestønadType
)

internal enum class HjelpestønadType {
    FORHØYET_SATS_3,
    FORHØYET_SATS_4;
}