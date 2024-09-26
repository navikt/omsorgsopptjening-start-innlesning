package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.person.external.pdl

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import com.github.tomakehurst.wiremock.matching.AnythingPattern
import com.github.tomakehurst.wiremock.matching.StringValuePattern
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType

private fun WireMockExtension.pdlResponse(fileName: String): StubMapping {
    return this.stubFor(
        WireMock.post(WireMock.urlPathEqualTo("/graphql"))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withStatus(202)
                    .withBodyFile(fileName)
            )
    )
}

fun WireMockExtension.`pdl bad request`(): StubMapping {
    return this.pdlResponse("error_bad_request.json")
}

fun WireMockExtension.`pdl error not_found`(): StubMapping {
    return this.pdlResponse("error_not_found.json")
}

fun WireMockExtension.`pdl server error`(): StubMapping {
    return this.pdlResponse("error_server_error.json")
}

fun WireMockExtension.`pdl ikke autentisert`(): StubMapping {
    return this.pdlResponse("error_unauthenticated.json")
}

fun WireMockExtension.`pdl ikke autorisert`(): StubMapping {
    return this.pdlResponse("error_unauthorized.json")
}

fun WireMockExtension.`pdl fnr ett i bruk`(): StubMapping {
    return this.pdlResponse("fnr_1bruk.json")
}

fun WireMockExtension.`pdl fnr ett i bruk en opphørt`(): StubMapping {
    return this.pdlResponse("fnr_1bruk_1opphort.json")
}

fun WireMockExtension.`pdl fnr ett i bruk pluss historisk`(): StubMapping {
    return this.pdlResponse("fnr_1bruk_pluss_historisk.json")
}

fun WireMockExtension.`pdl fnr ett i bruk ett hopphørt`(): StubMapping {
    return this.pdlResponse("fnr_1opphort.json")
}

fun WireMockExtension.`pdl samme fnr er gjeldende og historisk`(): StubMapping {
    return this.pdlResponse("fnr_samme_fnr_gjeldende_og_historisk.json")
}

fun WireMockExtension.`pdl gjeldende og historisk fnr er det samme`(): StubMapping {
    return this.pdlResponse("historikk_and_gjeldende_fnr_are_equal.json")
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
                                        "barnetrygdPerioder":[
                                            {
                                                "personIdent":"09876543210",
                                                "delingsprosentYtelse":"FULL",
                                                "ytelseTypeEkstern":"ORDINÆR_BARNETRYGD",
                                                "utbetaltPerMnd":2000,
                                                "stønadFom": "2020-01",
                                                "stønadTom": "2025-12",
                                                "sakstypeEkstern":"NASJONAL",
                                                "kildesystem":"BA",
                                                "pensjonstrygdet":null,
                                                "norgeErSekundærlandMedNullUtbetaling":false
                                            }
                                        ]
                                    }
                                ]
                            }
                        """.trimIndent()
                    )
            )
    )
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

fun WireMockExtension.`hent-barnetrygd ok uten fagsaker`(): StubMapping {
    return this.stubFor(
        WireMock.post(WireMock.urlPathEqualTo("/api/ekstern/pensjon/hent-barnetrygd"))
            .withExpectedRequestHeadersHentBarnetryd()
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

fun WireMockExtension.`hent hjelpestønad ok - har hjelpestønad`(): StubMapping {
    return this.stubFor(
        WireMock.get(WireMock.urlPathEqualTo("/api/hjelpestonad"))
            .willReturn(
                WireMock.ok()
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(
                        """
                                [
                                    {
                                        "id":"123",
                                        "ident":"09876543210",
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

