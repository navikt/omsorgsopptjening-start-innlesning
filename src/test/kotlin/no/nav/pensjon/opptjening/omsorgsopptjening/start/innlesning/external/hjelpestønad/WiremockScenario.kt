package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.hjelpestønad

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Ident
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.barnetrygd.WiremockFagsak.Companion.formatterForKall
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.lang.String.format
import java.time.YearMonth
import java.util.concurrent.atomic.AtomicInteger

fun WireMockExtension.`hent hjelpestønad ok - ingen hjelpestønad`(forFnr: Ident): StubMapping {
    return this.stubFor(
        post(urlPathEqualTo("/api/hjelpestonad/hent"))
            .withRequestBody(
                matchingJsonPath("$.fnr", equalTo(forFnr.value))
            )
            .willReturn(
                ok()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(
                        """
                                []
                            """.trimIndent()
                    )
            )
    )
}

fun WireMockExtension.`hent hjelpestønad ok - ingen hjelpestønad`(): StubMapping {
    return this.stubFor(
        post(urlPathEqualTo("/api/hjelpestonad/hent"))
            .willReturn(
                ok()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(
                        """
                                []
                            """.trimIndent()
                    )
            )
    )
}

fun WireMockExtension.`hent hjelpestønad - echo request`(): StubMapping {
    return this.stubFor(
        post(urlPathEqualTo("/api/hjelpestonad/hent"))
            .willReturn(
                ok()
                    .withTransformers("response-template")
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(
                        """
                                {{{ request.body }}}
                            """.trimIndent()
                    )
            )
    )
}

fun WireMockExtension.`hent hjelpestønad ok - har hjelpestønad`(
    forFnr: Ident,
    fom: YearMonth = YearMonth.of(2020, 1),
    tom: YearMonth = YearMonth.of(2025, 12),
    omsorgstype: String = "FORHØYET_SATS_3",
): StubMapping {
    return this.stubFor(
        post(urlPathEqualTo("/api/hjelpestonad/hent"))
            .withRequestBody(
                matchingJsonPath("$.fnr", equalTo(forFnr.value))
            )
            .willReturn(
                ok()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(
                        """
                                [
                                    {
                                        "id":"123",
                                        "ident":"${forFnr.value}",
                                        "fom":"${fom.formatterForKall()}",
                                        "tom":"${tom.formatterForKall()}",
                                        "omsorgstype":"${omsorgstype}"
                                    }
                                ]
                            """.trimIndent()
                    )
            )
    )
}

private val sequence = AtomicInteger(100)

fun resetHjelpestønadSequence() {
    sequence.set(100)
}

fun WireMockExtension.`hent hjelpestønad ok - har hjelpestønad`(): StubMapping {
    return this.stubFor(
        post(urlPathEqualTo("/api/hjelpestonad/hent"))
            .willReturn(
                ok()
                    .withTransformers("response-template")
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withTransformerParameter("seq", format("%d", sequence.incrementAndGet()))
                    .withBody(
                        """
                            {{regexExtract request.body '"fnr"\:"([0-9]+)"' 'fnr'}}
                                [
                                    {
                                        "id":"{{parameters.seq}}",
                                        "ident":"{{fnr}}",
                                        "fom":"2020-01",
                                        "tom":"2025-12",
                                        "omsorgstype":"FORHØYET_SATS_3"
                                    }
                                ]
                            """.trimIndent()
                    )
            )
    )
}