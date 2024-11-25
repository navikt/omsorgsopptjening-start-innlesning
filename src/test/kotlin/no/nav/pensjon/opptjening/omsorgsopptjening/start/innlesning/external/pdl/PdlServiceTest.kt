package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.pdl

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Mdc
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.SpringContextTest
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Ident
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.pdl.PdlErrorCode
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.pdl.PdlException
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.pdl.PdlService
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.config.TokenProviderConfig.Companion.MOCK_TOKEN
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.PersonOppslagException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.RestClientException

internal class PdlServiceTest : SpringContextTest.NoKafka() {

    @Autowired
    lateinit var pdlService: PdlService

    companion object {
        val FNR = Ident("11111111111")

        @JvmField
        @RegisterExtension
        val wiremock = WireMockExtension.newInstance()
            .options(wireMockConfig().port(WIREMOCK_PORT))
            .build()!!
    }

    @Test
    fun `Given hentPerson then call pdl one time`() {
        Mdc.scopedMdc(CorrelationId.generate()) {
            Mdc.scopedMdc(InnlesingId.generate()) {
                wiremock.`pdl fnr fra query`()
                pdlService.hentPerson(FNR)

                wiremock.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo(PDL_PATH)))
            }
        }
    }

    @Test
    fun `Given hentPerson Then call pdl with fnr`() {
        Mdc.scopedMdc(CorrelationId.generate()) {
            Mdc.scopedMdc(InnlesingId.generate()) {
                wiremock.`pdl fnr fra query`()

                pdlService.hentPerson(FNR)

                wiremock.verify(
                    WireMock.postRequestedFor(WireMock.urlEqualTo(PDL_PATH)).withRequestBody(containing(FNR.value))
                )
            }
        }
    }

    @Test
    fun `Given hentPerson Then call pdl with token and other headers`() {
        Mdc.scopedMdc(CorrelationId.generate()) {
            Mdc.scopedMdc(InnlesingId.generate()) {
                wiremock.`pdl fnr fra query`()

                pdlService.hentPerson(FNR)

                wiremock.verify(
                    WireMock.postRequestedFor(WireMock.urlEqualTo(PDL_PATH))
                        .withHeader("Authorization", WireMock.equalTo("Bearer $MOCK_TOKEN"))
                        .withHeader("Accept", WireMock.equalTo("application/json"))
                        .withHeader("Content-Type", WireMock.equalTo("application/json"))
                        .withHeader("Nav-Consumer-Id", WireMock.equalTo("omsorgsopptjening-start-innlesning"))
                        .withHeader("Tema", WireMock.equalTo("PEN"))
                        .withHeader(
                            "x-correlation-id",
                            WireMock.matching("^[0-9a-fA-F]{8}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{12}\$")
                        )
                        .withHeader(
                            "Nav-Call-Id",
                            WireMock.matching("^[0-9a-fA-F]{8}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{12}\$")
                        )
                )
            }
        }
    }

    @Test
    fun `Given PDL return folkeregisteridentifikator When hentPerson Then return pdl response`() {
        Mdc.scopedMdc(CorrelationId.generate()) {
            Mdc.scopedMdc(InnlesingId.generate()) {
                wiremock.`pdl fnr fra query`()

                assertThat(pdlService.hentPerson(FNR)).isNotNull()
            }
        }
    }

    @Test
    @Disabled("Treig som følge av backoff ved retry")
    fun `Given other code than 200 When getting person Then retry 3 times before give up`() {
        Mdc.scopedMdc(CorrelationId.generate()) {
            Mdc.scopedMdc(InnlesingId.generate()) {
                wiremock.stubFor(
                    WireMock.post(WireMock.urlEqualTo(PDL_PATH)).willReturn(WireMock.aResponse().withStatus(401))
                )
                assertThrows<RestClientException> { pdlService.hentPerson(FNR) }
                wiremock.verify(4, WireMock.postRequestedFor(WireMock.urlEqualTo(PDL_PATH)))
            }
        }
    }

    @Test
    @Disabled("Treig som følge av backoff ved retry")
    fun `Given server error When getting person Then retry 3 times before give up`() {
        Mdc.scopedMdc(CorrelationId.generate()) {
            Mdc.scopedMdc(InnlesingId.generate()) {
                wiremock.stubFor(
                    WireMock.post(WireMock.urlEqualTo(PDL_PATH)).willReturn(
                        WireMock.aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBodyFile("pdl/error_server_error.json")
                    )
                )

                val error = assertThrows<PdlException> { pdlService.hentPerson(FNR) }
                assertThat(error.code).isEqualTo(PdlErrorCode.SERVER_ERROR)
                wiremock.verify(4, WireMock.postRequestedFor(WireMock.urlEqualTo(PDL_PATH)))
            }
        }

    }

    @Test
    fun `Given not found When calling pdl Then throw PdlException`() {
        Mdc.scopedMdc(CorrelationId.generate()) {
            Mdc.scopedMdc(InnlesingId.generate()) {
                wiremock.stubFor(
                    WireMock.post(WireMock.urlEqualTo(PDL_PATH)).willReturn(
                        WireMock.aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBodyFile("pdl/error_not_found.json")
                    )
                )

                val error = assertThrows<PersonOppslagException> { pdlService.hentPerson(FNR) }
                assertInstanceOf(PdlException::class.java, error.cause).also {
                    assertThat(it.code).isEqualTo(PdlErrorCode.NOT_FOUND)
                }
                wiremock.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo(PDL_PATH)))
            }
        }
    }

    @Test
    fun `Given unauthenticated When calling pdl Then throw PdlException`() {
        Mdc.scopedMdc(CorrelationId.generate()) {
            Mdc.scopedMdc(InnlesingId.generate()) {
                wiremock.stubFor(
                    WireMock.post(WireMock.urlEqualTo(PDL_PATH)).willReturn(
                        WireMock.aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBodyFile("pdl/error_unauthenticated.json")
                    )
                )

                val error = assertThrows<PersonOppslagException> { pdlService.hentPerson(FNR) }
                assertInstanceOf(PdlException::class.java, error.cause).also {
                    assertThat(it.code).isEqualTo(PdlErrorCode.UNAUTHENTICATED)
                }
                wiremock.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo(PDL_PATH)))
            }
        }
    }

    @Test
    fun `Given unauthorized When calling pdl Then throw PdlException`() {
        Mdc.scopedMdc(CorrelationId.generate()) {
            Mdc.scopedMdc(InnlesingId.generate()) {
                wiremock.`pdl - unauthorized`()
                val error = assertThrows<PersonOppslagException> { pdlService.hentPerson(FNR) }
                assertInstanceOf(PdlException::class.java, error.cause).also {
                    assertThat(it.code).isEqualTo(PdlErrorCode.UNAUTHORIZED)
                }
                wiremock.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo(PDL_PATH)))
            }
        }
    }

    @Test
    fun `Given bad request When calling pdl Then throw PdlException`() {
        Mdc.scopedMdc(CorrelationId.generate()) {
            Mdc.scopedMdc(InnlesingId.generate()) {
                wiremock.stubFor(
                    WireMock.post(WireMock.urlEqualTo(PDL_PATH)).willReturn(
                        WireMock.aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBodyFile("pdl/error_bad_request.json")
                    )
                )

                val error = assertThrows<PersonOppslagException> { pdlService.hentPerson(FNR) }
                assertInstanceOf(PdlException::class.java, error.cause).also {
                    assertThat(it.code).isEqualTo(PdlErrorCode.BAD_REQUEST)
                }
                wiremock.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo(PDL_PATH)))
            }
        }
    }
}