package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.barnetrygd

import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Kilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Landstilknytning
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Omsorgstype
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.PersongrunnlagMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Mdc
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.SpringContextTest
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.GyldigÅrsintervallFilter
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Ident
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.År
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.barnetrygd.BarnetrygdClient
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.barnetrygd.BestillBarnetrygdMottakereException
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.barnetrygd.BestillBarnetrygdmottakereResponse
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.barnetrygd.HentBarnetrygdException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import org.skyscreamer.jsonassert.JSONAssert
import org.springframework.beans.factory.annotation.Autowired
import java.time.Month
import java.time.YearMonth
import kotlin.test.assertContains

class BarnetrygdClientTest : SpringContextTest.NoKafka() {

    @Autowired
    private lateinit var client: BarnetrygdClient

    companion object {
        @JvmField
        @RegisterExtension
        val wiremock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
            .build()!!
    }

    @Nested
    inner class BestillPersonerMedBarnetrygd {
        @Test
        fun `returner ok dersom kall til bestill-personer-med-barnetrygd svarer med accepted`() {
            wiremock.`bestill-personer-med-barnetrygd accepted`()

            client.bestillBarnetrygdmottakere(ar = År(2020)).also {
                assertEquals(
                    BestillBarnetrygdmottakereResponse(
                        InnlesingId.fromString("3d797c7d-6273-4be3-bd57-e13de35251f8"),
                        År(2020),
                    ), it
                )
            }
        }

        @Test
        fun `kaster exception dersom kall til bestill-personer-med-barnetrygd svarer med noe annet enn accepted`() {
            wiremock.`bestill-personer-med-barnetrygd internal server error`()

            assertThrows<BestillBarnetrygdMottakereException> {
                client.bestillBarnetrygdmottakere(ar = År(2020))
            }.also {
                assertContains(it.msg, "500")
                assertContains(it.msg, "{whatever this may contain}")
            }
        }
    }

    @Nested
    inner class HentBarnetrygd {
        @Test
        fun `returner ok dersom kall til hent-barnetrygd svarer med 200`() {
            Mdc.scopedMdc(CorrelationId.generate()) {
                Mdc.scopedMdc(InnlesingId.generate()) {
                    wiremock.`hent-barnetrygd ok`(Ident("123"))

                    client.hentBarnetrygd(
                        ident = "123",
                        gyldigÅrsintervall = GyldigÅrsintervallFilter(2020)
                    ).also {
                        assertThat(
                            it.barnetrygdsaker
                        ).isEqualTo(
                            listOf(
                                PersongrunnlagMelding.Persongrunnlag.of(
                                    omsorgsyter = "12345678910",
                                    omsorgsperioder = listOf(
                                        PersongrunnlagMelding.Omsorgsperiode(
                                            omsorgsmottaker = "09876543210",
                                            omsorgstype = Omsorgstype.FULL_BARNETRYGD,
                                            fom = YearMonth.of(2020, Month.JANUARY),
                                            tom = YearMonth.of(2021, Month.DECEMBER),
                                            kilde = Kilde.BARNETRYGD,
                                            utbetalt = 2000,
                                            landstilknytning = Landstilknytning.NORGE
                                        )
                                    ),
                                    hjelpestønadsperioder = emptyList(),
                                )
                            )
                        )

                        assertThat(it.rådataFraKilde["fnr"]).isEqualTo("123")
                        assertThat(it.rådataFraKilde["fom"]).isEqualTo("2020-01-01")
                        JSONAssert.assertEquals(
                            it.rådataFraKilde["barnetrygd"],
                            """{
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
                                }""".trimIndent(),
                            false,
                        )
                    }
                }
            }
        }

        @Test
        fun `kaster exception dersom kall til hent-barnetrygd svarer med noe annet enn 200`() {
            Mdc.scopedMdc(CorrelationId.generate()) {
                Mdc.scopedMdc(InnlesingId.generate()) {
                    wiremock.`hent-barnetrygd internal server error`()

                    assertThrows<HentBarnetrygdException> {
                        client.hentBarnetrygd(
                            ident = "123",
                            gyldigÅrsintervall = GyldigÅrsintervallFilter(2020)
                        )
                    }.also {
                        assertContains(it.msg, "FUNKSJONELL_FEIL")
                        assertContains(it.msg, "Dette gikk ikke så bra")
                    }
                }
            }
        }

        @Disabled
        /* TODO: temporært, i påvente av avklaring */
        @Test
        fun `kaster exception dersom kall til hent-barnetrygd svarer med 200 ok med tom liste`() {
            Mdc.scopedMdc(CorrelationId.generate()) {
                Mdc.scopedMdc(InnlesingId.generate()) {
                    wiremock.`hent-barnetrygd ok uten fagsaker`(Ident("123"))

                    assertThrows<HentBarnetrygdException> {
                        client.hentBarnetrygd(
                            ident = "123",
                            gyldigÅrsintervall = GyldigÅrsintervallFilter(2020)
                        )
                    }.also {
                        assertThat(it.msg).contains("Liste med barnetrygdsaker er tom")
                    }
                }
            }
        }

        @Test
        fun `kaster exception dersom kall til hent-barnetrygd svarer med 200 ok men uten liste`() {
            Mdc.scopedMdc(CorrelationId.generate()) {
                Mdc.scopedMdc(InnlesingId.generate()) {
                    wiremock.`hent-barnetrygd ok uten fagsakfelt`()

                    assertThrows<HentBarnetrygdException> {
                        client.hentBarnetrygd(
                            ident = "123",
                            gyldigÅrsintervall = GyldigÅrsintervallFilter(2020)
                        )
                    }.also {
                        assertThat(it.msg).contains("Liste med barnetrygdsaker mangler")
                    }
                }
            }
        }


        @Test
        @Disabled("restkjøring av prodsaker")
        fun `kaster exception dersom kall til hent-barnetrygd svarer med 200 ok og saker mangler barnetrygdperioder`() {
            Mdc.scopedMdc(CorrelationId.generate()) {
                Mdc.scopedMdc(InnlesingId.generate()) {
                    wiremock.`hent-barnetrygd ok uten barnetrygdperioder`()

                    assertThrows<HentBarnetrygdException> {
                        client.hentBarnetrygd(
                            ident = "123",
                            gyldigÅrsintervall = GyldigÅrsintervallFilter(2020)
                        )
                    }.also {
                        assertThat(it.msg).contains("En eller flere av barnetrygdsakene mangler perioder")
                    }
                }
            }
        }
    }
}