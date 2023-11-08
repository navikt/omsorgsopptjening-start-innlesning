package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external

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

