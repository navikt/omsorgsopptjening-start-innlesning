package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import com.github.tomakehurst.wiremock.matching.AnythingPattern
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.barnetrygd.Barnetrygdmelding
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.serialize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType

class BarnetrygdClientTest : SpringContextTest.NoKafka() {

    @Autowired
    private lateinit var client: BarnetrygdClient

    companion object {
        @RegisterExtension
        private val wiremock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
            .build()!!
    }

    @Test
    fun `returner ok dersom kall til hent-barnetrygdmottakere går bra`() {
        wiremock.stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/api/ekstern/pensjon/bestill-personer-med-barnetrygd/2020"))
                .withHeader(CorrelationId.name, AnythingPattern())
                .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(MediaType.APPLICATION_JSON_VALUE))
                .withHeader(HttpHeaders.ACCEPT, equalTo(MediaType.APPLICATION_JSON_VALUE))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer test.token.test"))
                .willReturn(WireMock.aResponse().withStatus(202).withBody("123enfinid"))
        )

        client.hentBarnetrygdmottakere(ar = 2020).also {
            assertEquals(HentBarnetygdmottakereResponse.Ok("123enfinid", 2020), it)
        }
    }

    @Test
    fun `returner feil med diverse informasjon dersom kall til hent-barnetrygdmottakere gir 500`() {
        wiremock.stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/api/ekstern/pensjon/bestill-personer-med-barnetrygd/2020"))
                .withHeader(CorrelationId.name, AnythingPattern())
                .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(MediaType.APPLICATION_JSON_VALUE))
                .withHeader(HttpHeaders.ACCEPT, equalTo(MediaType.APPLICATION_JSON_VALUE))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer test.token.test"))
                .willReturn(
                    WireMock.serverError()
                        .withBody(
                            """
                                [
                                    {
                                       "data": {
                                            "key":"value"
                                       },
                                       "status":"FEILET",
                                       "melding":"Her ble det bare krøll",
                                       "frontendMelding": "test",
                                       "stacktrace": null
                                    }
                                ]
                            """.trimIndent()
                        )
                )
        )

        client.hentBarnetrygdmottakere(ar = 2020).also {
            assertEquals(
                HentBarnetygdmottakereResponse.Feil(
                    500,
                    """[InternalServerErrorResponse(data={key=value}, status=FEILET, melding=Her ble det bare krøll, frontendMelding=test, stacktrace=null)]"""
                ), it
            )
        }
    }

    @Test
    fun `returner feil dersom kall til hent-barnetrygdmottakere gir andre feil enn 500`() {
        wiremock.stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/api/ekstern/pensjon/bestill-personer-med-barnetrygd/2020"))
                .withHeader(CorrelationId.name, AnythingPattern())
                .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(MediaType.APPLICATION_JSON_VALUE))
                .withHeader(HttpHeaders.ACCEPT, equalTo(MediaType.APPLICATION_JSON_VALUE))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer test.token.test"))
                .willReturn(
                    WireMock.forbidden().withBody("Forbidden!")
                )
        )

        client.hentBarnetrygdmottakere(ar = 2020).also {
            assertEquals(
                HentBarnetygdmottakereResponse.Feil(
                    403,
                    "Forbidden!"
                ), it
            )
        }
    }

    @Test
    fun `returner ok dersom kall til hent-barnetrygd går bra`() {
        wiremock.stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/api/ekstern/pensjon/hent-barnetrygd"))
                .withHeader(CorrelationId.name, AnythingPattern())
                .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(MediaType.APPLICATION_JSON_VALUE))
                .withHeader(HttpHeaders.ACCEPT, equalTo(MediaType.APPLICATION_JSON_VALUE))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer test.token.test"))
                .willReturn(
                    WireMock.ok()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(
                            serialize(
                                listOf(
                                    Barnetrygdmelding.Sak(
                                        fagsakId = "1",
                                        fagsakEiersIdent = "11",
                                        barnetrygdPerioder = emptyList()
                                    )
                                )
                            )
                        )
                )
        )

        client.hentBarnetrygd(
            ident = "123",
            ar = 2020
        ).also {
            assertEquals(
                HentBarnetrygdResponse.Ok(
                    listOf(
                        Barnetrygdmelding.Sak(
                            fagsakId = "1",
                            fagsakEiersIdent = "11",
                            barnetrygdPerioder = emptyList()
                        )
                    )
                ), it
            )
        }
    }

    @Test
    fun `returner feil med diverse informasjon dersom kall til hent-barnetrygd gir 500`() {
        wiremock.stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/api/ekstern/pensjon/hent-barnetrygd"))
                .withHeader(CorrelationId.name, AnythingPattern())
                .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(MediaType.APPLICATION_JSON_VALUE))
                .withHeader(HttpHeaders.ACCEPT, equalTo(MediaType.APPLICATION_JSON_VALUE))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer test.token.test"))
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

        client.hentBarnetrygd(
            ident = "123",
            ar = 2020
        ).also {
            assertEquals(
                HentBarnetrygdResponse.Feil(
                    500,
                    """[InternalServerErrorResponse(data=null, status=FUNKSJONELL_FEIL, melding=Dette gikk ikke så bra, frontendMelding=null, stacktrace=null)]"""
                ), it
            )
        }
    }

    @Test
    fun `returner feil dersom kall til hent-barnetrygd gir andre feil enn 500`() {
        wiremock.stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/api/ekstern/pensjon/hent-barnetrygd"))
                .withHeader(CorrelationId.name, AnythingPattern())
                .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(MediaType.APPLICATION_JSON_VALUE))
                .withHeader(HttpHeaders.ACCEPT, equalTo(MediaType.APPLICATION_JSON_VALUE))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer test.token.test"))
                .willReturn(
                    WireMock.forbidden().withBody("Forbidden!")
                )
        )

        client.hentBarnetrygd(
            ident = "123",
            ar = 2020
        ).also {
            assertEquals(
                HentBarnetrygdResponse.Feil(
                    403,
                    "Forbidden!"
                ), it
            )
        }
    }
}