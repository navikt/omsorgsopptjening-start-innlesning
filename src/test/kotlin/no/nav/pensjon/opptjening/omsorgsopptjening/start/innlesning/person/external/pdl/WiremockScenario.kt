package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.person.external.pdl

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import com.github.tomakehurst.wiremock.stubbing.StubMapping

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