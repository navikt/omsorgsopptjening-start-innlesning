package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.pdl

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.or
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Ident

private fun WireMockExtension.pdlResponse(fileName: String): StubMapping {
    synchronized(this) {
        return this.stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/graphql"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(202)
                        .withBodyFile("pdl/$fileName")
                )
        )
    }
}

private fun WireMockExtension.pdlResponse(fnr: String, fileName: String): StubMapping {
    synchronized(this) {
        return this.stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/graphql"))
                .withRequestBody(WireMock.containing(""""variables":{"ident":"$fnr"}"""))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(202)
                        .withBodyFile("pdl/$fileName")
                )
        )
    }
}

fun WireMockExtension.pdl(fnr: Ident, historiske: List<Ident>): StubMapping {
    val fnrs = historiske.map { it.value }.toSet().plus(fnr.value)
    println("PDL: fnrs=$fnrs")
    return this.stubFor(
        WireMock.post(WireMock.urlPathEqualTo("/graphql"))
            .withRequestBody(
                or(
                    *fnrs.map { fnr: String ->
                        WireMock.containing(""""variables":{"ident":"$fnr"}""")
                    }.toTypedArray()
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

fun WireMockExtension.`pdl error not_found`(): StubMapping {
    return this.pdlResponse("error_not_found.json")
}

fun WireMockExtension.`pdl error not_found`(fnr: String): StubMapping {
    return this.pdlResponse(fnr, "error_not_found.json")
}

fun WireMockExtension.`pdl fnr ett i bruk`(): StubMapping {
    return this.pdlResponse("fnr_1bruk.json")
}