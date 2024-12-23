package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.pdl

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.or
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.SpringContextTest.Companion.PDL_PATH
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Ident

fun WireMockExtension.`pdl error not_found`(): StubMapping {
    synchronized(this) {
        return this.stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/graphql"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(202)
                        .withBodyFile("pdl/error_not_found.json")
                )
        )
    }
}

fun WireMockExtension.`pdl error not_found`(fnr: String): StubMapping {
    synchronized(this) {
        return this.stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/graphql"))
                .withRequestBody(WireMock.containing(""""variables":{"ident":"$fnr"}"""))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(202)
                        .withBodyFile("pdl/error_not_found.json")
                )
        )
    }
}

fun WireMockExtension.pdl(fnr: Ident, historiske: List<Ident>): StubMapping {
    val fnrs = historiske.map { it.value }.toSet().plus(fnr.value)
    return this.stubFor(
        WireMock.post(WireMock.urlPathEqualTo("/graphql"))
            .withRequestBody(
                or(
                    *fnrs.map { fnr: String ->
                        WireMock.containing(""""variables":{"ident":"$fnr"}""")
                    }.plus(WireMock.containing("FINNES_IKKE_I_FILEN")) // må til fordi or krever minst to matchers
                        .toTypedArray()
                )
            )
            .willReturn(
                aResponse()
                    .withTransformers("response-template")
                    .withHeader("Content-Type", "application/json")
                    .withStatus(202)
                    .withTransformerParameter("fnr", fnr.value)
                    .withTransformerParameter("historiske", historiske.map { it.value })
                    .withBodyFile(
                        "pdl/fnr_template.json"
                    )
            )
    )
}

fun WireMockExtension.`pdl - ingen gjeldende`(historiske: List<Ident>): StubMapping {
    val fnrs = historiske.map { it.value }.toSet()
    return this.stubFor(
        WireMock.post(WireMock.urlPathEqualTo("/graphql"))
            .withRequestBody(
                or(
                    *fnrs.map { fnr: String ->
                        WireMock.containing(""""variables":{"ident":"$fnr"}""")
                    }.plus(WireMock.containing("FINNES_IKKE_I_FILEN")) // må til fordi or krever minst to matchers
                        .toTypedArray()
                )
            )
            .willReturn(
                aResponse()
                    .withTransformers("response-template")
                    .withHeader("Content-Type", "application/json")
                    .withStatus(202)
                    .withTransformerParameter("historiske", historiske.map { it.value })
                    .withBodyFile(
                        "pdl/fnr_template_uten_gjeldende.json"
                    )
            )
    )
}


fun WireMockExtension.`pdl fnr fra query`(): StubMapping {
    return this.stubFor(
        WireMock.post(WireMock.urlPathEqualTo("/graphql"))
            .willReturn(
                aResponse()
                    .withTransformers("response-template")
                    .withHeader("Content-Type", "application/json")
                    .withStatus(202)
                    .withBodyFile(
                        "pdl/fnr_fra_query.json"
                    )
            )
    )
}

fun WireMockExtension.`pdl - unauthorized`(): StubMapping {
    return this.stubFor(
        WireMock.post(WireMock.urlEqualTo(PDL_PATH)).willReturn(
            aResponse()
                .withHeader("Content-Type", "application/json")
                .withBodyFile("pdl/error_unauthorized.json")
        )
    )
}