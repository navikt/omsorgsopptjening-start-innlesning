package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.pdl

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Mdc
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.SpringContextTest
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Ident
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.PersonId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.PersonOppslag
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.PersonOppslagException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired

internal class PersonIdOppslagTest : SpringContextTest.NoKafka() {

    @Autowired
    lateinit var personOppslag: PersonOppslag

    companion object {
        val FNR = Ident("11111111111")

        @JvmField
        @RegisterExtension
        val wiremock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
            .build()!!
    }

    @Test
    fun `Et fnr i bruk - Ett fnr i person`() {
        Mdc.scopedMdc(CorrelationId.generate()) {
            Mdc.scopedMdc(InnlesingId.generate()) {
                wiremock.stubFor(
                    WireMock.post(WireMock.urlEqualTo(PDL_PATH)).willReturn(
                        WireMock.aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBodyFile("pdl/fnr_1bruk.json")
                    )
                )
                val personId: PersonId = personOppslag.hentPerson(FNR)
                assertThat(personId.fnr).isEqualTo(Ident("12345678910"))
            }
        }
    }

    @Test
    fun `Samme historiske fnr som gjeldende - et fnr i person`() {
        Mdc.scopedMdc(CorrelationId.generate()) {
            Mdc.scopedMdc(InnlesingId.generate()) {
                wiremock.stubFor(
                    WireMock.post(WireMock.urlEqualTo(PDL_PATH)).willReturn(
                        WireMock.aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBodyFile("pdl/fnr_samme_fnr_gjeldende_og_historisk.json")
                    )
                )
                val personId: PersonId = personOppslag.hentPerson(FNR)
                assertThat(personId.fnr).isEqualTo(Ident("04010012797"))
            }
        }
    }


    @Test
    fun `Et fnr 1 OPPHOERT - kast exception`() {
        Mdc.scopedMdc(CorrelationId.generate()) {
            Mdc.scopedMdc(InnlesingId.generate()) {
                wiremock.stubFor(
                    WireMock.post(WireMock.urlEqualTo(PDL_PATH)).willReturn(
                        WireMock.aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBodyFile("pdl/fnr_1opphort.json")
                    )
                )
                assertThrows<PersonOppslagException> { personOppslag.hentPerson(FNR) }
            }
        }
    }

    @Test
    fun `Et fnr 0 OPPHOERT 0 BRUK - kast exception`() {
        Mdc.scopedMdc(CorrelationId.generate()) {
            Mdc.scopedMdc(InnlesingId.generate()) {
                wiremock.stubFor(
                    WireMock.post(WireMock.urlEqualTo(PDL_PATH)).willReturn(
                        WireMock.aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBodyFile("pdl/fnr_0bruk_0opphort.json")
                    )
                )
                assertThatThrownBy {
                    personOppslag.hentPerson(FNR)
                }
                    .isInstanceOf(PersonOppslagException::class.java)
            }
        }
    }
}
