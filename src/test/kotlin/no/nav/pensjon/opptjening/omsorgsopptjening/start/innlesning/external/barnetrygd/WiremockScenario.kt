package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.barnetrygd

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import com.github.tomakehurst.wiremock.matching.AnythingPattern
import com.github.tomakehurst.wiremock.matching.StringValuePattern
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Ident
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType

fun WireMockExtension.`bestill-personer-med-barnetrygd accepted`(): StubMapping {
    return this.stubFor(
        WireMock.get(WireMock.urlPathEqualTo("/api/ekstern/pensjon/bestill-personer-med-barnetrygd/2020"))
            .withExpectedRequestHeadersBestillPersonerMedBarnetrygd()
            .willReturn(
                WireMock.aResponse()
                    .withStatus(202)
                    .withBody("3d797c7d-6273-4be3-bd57-e13de35251f8")
            )
    )
}

fun WireMockExtension.`bestill-personer-med-barnetrygd internal server error`(): StubMapping {
    return this.stubFor(
        WireMock.get(WireMock.urlPathEqualTo("/api/ekstern/pensjon/bestill-personer-med-barnetrygd/2020"))
            .withExpectedRequestHeadersBestillPersonerMedBarnetrygd()
            .willReturn(
                WireMock.serverError()
                    .withBody("""{whatever this may contain}""")
            )
    )
}

private fun MappingBuilder.withExpectedRequestHeadersBestillPersonerMedBarnetrygd(
    headers: List<Pair<String, StringValuePattern>> = listOf(
        CorrelationId.identifier to AnythingPattern(),
        InnlesingId.identifier to AnythingPattern(),
        HttpHeaders.ACCEPT to WireMock.equalTo(MediaType.TEXT_PLAIN_VALUE),
        HttpHeaders.AUTHORIZATION to WireMock.equalTo("Bearer test.token.test"),
    )
): MappingBuilder {
    headers.forEach {
        this.withHeader(it.first, it.second)
    }
    return this
}

fun WireMockExtension.`hent-barnetrygd ok`(): StubMapping {
    return this.`hent-barnetrygd-med-fagsaker`(
        fagsaker = listOf(
            WiremockFagsak(
                eier = Ident("12345678910"),
                perioder = listOf(
                    WiremockFagsak.BarnetrygdPeriode(
                        personIdent = Ident("09876543210"),
                        utbetaltPerMnd = 2000,
                        stønadFom = "2020-01",
                        stønadTom = "2025-12",
                    )
                )
            )
        )
    )
}

fun WireMockExtension.`hent-barnetrygd ok`(forFnr: Ident): StubMapping {
    return this.`hent-barnetrygd-med-fagsaker`(
        forFnr = forFnr,
        fagsaker = listOf(
            WiremockFagsak(
                eier = Ident("12345678910"),
                perioder = listOf(
                    WiremockFagsak.BarnetrygdPeriode(
                        personIdent = Ident("09876543210"),
                        utbetaltPerMnd = 2000,
                        stønadFom = "2020-01",
                        stønadTom = "2025-12",
                    )
                )
            )
        )
    )
}


fun WireMockExtension.`hent-barnetrygd-med-fagsaker`(forFnr: Ident, fagsaker: List<WiremockFagsak>): StubMapping {
    synchronized(this) {
        val fagsakMap = fagsaker.map { it.toMap() }
        return this.stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/api/ekstern/pensjon/hent-barnetrygd"))
                .withExpectedRequestHeadersHentBarnetryd()
                .withRequestBody(matchingJsonPath("$.ident", WireMock.equalTo(forFnr.value)))
                .willReturn(
                    WireMock.ok()
                        .withLogNormalRandomDelay(1000.0, 0.0)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withTransformers("response-template")
                        .withTransformerParameter("foo", "bar")
                        .withTransformerParameter("fagsaker", fagsakMap)
                        .withBodyFile("barnetrygd/fagsaker.json")
                )
        )
    }
}

fun WireMockExtension.`hent-barnetrygd-med-fagsaker`(fagsaker: List<WiremockFagsak>): StubMapping {
    synchronized(this) {
        println("Wiremock: fagsaker: $fagsaker")
        val a = fagsaker.map { it.toMap() }
        println("Wiremock: fagsaker: $fagsaker")
        return this.stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/api/ekstern/pensjon/hent-barnetrygd"))
                .withExpectedRequestHeadersHentBarnetryd()
                .willReturn(
                    WireMock.ok()
                        .withLogNormalRandomDelay(1000.0, 0.0)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withTransformers("response-template")
                        .withTransformerParameter("foo", "bar")
                        .withTransformerParameter("fagsaker", a)
                        .withBodyFile("barnetrygd/fagsaker.json")
                )
        )
    }
}

fun WireMockExtension.`hent-barnetrygd ok - ingen perioder`(): StubMapping {
    return this.stubFor(
        WireMock.post(WireMock.urlPathEqualTo("/api/ekstern/pensjon/hent-barnetrygd"))
            .withExpectedRequestHeadersHentBarnetryd()
            .willReturn(
                WireMock.ok()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(
                        """
                            {
                                "fagsaker": [
                                    {
                                        "fagsakEiersIdent":"12345678910",
                                        "barnetrygdPerioder":[]
                                    }
                                ]
                            }
                        """.trimIndent()
                    )
            )
    )
}

fun WireMockExtension.`hent-barnetrygd ok uten fagsaker`(forFnr: Ident): StubMapping {
    return this.stubFor(
        WireMock.post(WireMock.urlPathEqualTo("/api/ekstern/pensjon/hent-barnetrygd"))
            .withExpectedRequestHeadersHentBarnetryd()
            .withRequestBody(matchingJsonPath("$.ident", WireMock.equalTo(forFnr.value)))
            .willReturn(
                WireMock.ok()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(
                        """
                            {
                                "fagsaker": []
                            }
                        """.trimIndent()
                    )
            )
    )
}

fun WireMockExtension.`hent-barnetrygd ok uten fagsakfelt`(): StubMapping {
    return this.stubFor(
        WireMock.post(WireMock.urlPathEqualTo("/api/ekstern/pensjon/hent-barnetrygd"))
            .withExpectedRequestHeadersHentBarnetryd()
            .willReturn(
                WireMock.ok()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(
                        """
                            {
                                
                            }
                        """.trimIndent()
                    )
            )
    )
}


fun WireMockExtension.`hent-barnetrygd ok uten barnetrygdperioder`(): StubMapping {
    return this.stubFor(
        WireMock.post(WireMock.urlPathEqualTo("/api/ekstern/pensjon/hent-barnetrygd"))
            .withExpectedRequestHeadersHentBarnetryd()
            .willReturn(
                WireMock.ok()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(
                        """
                            {
                                "fagsaker": [
                                    {
                                        "fagsakEiersIdent":"12345678910",
                                        "barnetrygdPerioder":[]
                                    }
                                ]
                            }
                        """.trimIndent()
                    )
            )
    )
}

fun WireMockExtension.`hent-barnetrygd internal server error`(): StubMapping {
    return this.stubFor(
        WireMock.post(WireMock.urlPathEqualTo("/api/ekstern/pensjon/hent-barnetrygd"))
            .withExpectedRequestHeadersHentBarnetryd()
            .willReturn(
                WireMock.serverError()
                    .withBody(
                        """
                                    [
                                        {
                                           "status":"FUNKSJONELL_FEIL",
                                           "melding":"Dette gikk ikke så bra"
                                        }
                                    ]
                            """.trimIndent()
                    )
            )
    )
}

private fun MappingBuilder.withExpectedRequestHeadersHentBarnetryd(
    headers: List<Pair<String, StringValuePattern>> = listOf(
        CorrelationId.identifier to AnythingPattern(),
        HttpHeaders.CONTENT_TYPE to WireMock.equalTo(MediaType.APPLICATION_JSON_VALUE),
        HttpHeaders.ACCEPT to WireMock.equalTo(MediaType.APPLICATION_JSON_VALUE),
        HttpHeaders.AUTHORIZATION to WireMock.equalTo("Bearer test.token.test"),
    )
): MappingBuilder {
    headers.forEach {
        this.withHeader(it.first, it.second)
    }
    return this
}

