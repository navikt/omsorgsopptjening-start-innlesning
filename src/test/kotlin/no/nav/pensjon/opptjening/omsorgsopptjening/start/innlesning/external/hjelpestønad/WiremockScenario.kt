package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.hjelpestønad

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.lang.String.format
import java.util.concurrent.atomic.AtomicInteger

fun WireMockExtension.`hent hjelpestønad ok - ingen hjelpestønad`(forFnr: String): StubMapping {
    return this.stubFor(
        WireMock.get(WireMock.urlPathEqualTo("/api/hjelpestonad"))
            .withHeader("fnr", WireMock.equalTo(forFnr))
            .willReturn(
                WireMock.ok()
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
        WireMock.get(WireMock.urlPathEqualTo("/api/hjelpestonad"))
            .willReturn(
                WireMock.ok()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(
                        """
                                []
                            """.trimIndent()
                    )
            )
    )
}


fun WireMockExtension.`hent hjelpestønad ok - har hjelpestønad`(forFnr: String): StubMapping {
    return this.stubFor(
        WireMock.get(WireMock.urlPathEqualTo("/api/hjelpestonad"))
            .withHeader("fnr", WireMock.equalTo(forFnr))
            .willReturn(
                WireMock.ok()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(
                        """
                                [
                                    {
                                        "id":"123",
                                        "ident":"$forFnr",
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

private val sequence = AtomicInteger(100)

fun resetHjelpestønadSequence() {
    sequence.set(100)
}

fun WireMockExtension.`hent hjelpestønad ok - har hjelpestønad`(): StubMapping {
    return this.stubFor(
        WireMock.get(WireMock.urlPathEqualTo("/api/hjelpestonad"))
            .willReturn(
                WireMock.ok()
                    .withTransformers("response-template")
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withTransformerParameter("seq", format("%d", sequence.incrementAndGet()))
                    .withBody(
                        """
                                [
                                    {
                                        "id":"{{parameters.seq}}",
                                        "ident":"{{request.headers.fnr}}",
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