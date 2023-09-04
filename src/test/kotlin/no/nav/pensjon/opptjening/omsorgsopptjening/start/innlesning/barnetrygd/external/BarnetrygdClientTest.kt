package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external

import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.RådataFraKilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.OmsorgsgrunnlagMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Mdc
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.SpringContextTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import java.time.Month
import java.time.YearMonth

class BarnetrygdClientTest : SpringContextTest.NoKafka() {

    @Autowired
    private lateinit var client: BarnetrygdClient

    companion object {
        @RegisterExtension
        private val wiremock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
            .build()!!
    }

    @Nested
    inner class BestillPersonerMedBarnetrygd {
        @Test
        fun `returner ok dersom kall til bestill-personer-med-barnetrygd svarer med accepted`() {
            wiremock.`bestill-personer-med-barnetrygd accepted`()

            client.bestillBarnetrygdmottakere(ar = 2020).also {
                assertEquals(
                    BestillBarnetrygdmottakereResponse.Ok(
                        InnlesingId.fromString("3d797c7d-6273-4be3-bd57-e13de35251f8"),
                        2020
                    ), it
                )
            }
        }

        @Test
        fun `returner feil med diverse informasjon dersom kall til bestill-personer-med-barnetrygd svarer med noe annet enn accepted`() {
            wiremock.`bestill-personer-med-barnetrygd internal server error`()

            client.bestillBarnetrygdmottakere(ar = 2020).also {
                assertEquals(
                    BestillBarnetrygdmottakereResponse.Feil(
                        500,
                        """{whatever this may contain}"""
                    ), it
                )
            }
        }
    }

    @Nested
    inner class HentBarnetrygd {
        @Test
        fun `returner ok dersom kall til hent-barnetrygd svarer med 200`() {
            Mdc.scopedMdc(CorrelationId.generate()) {
                Mdc.scopedMdc(InnlesingId.generate()) {
                    wiremock.`hent-barnetrygd ok`()

                    client.hentBarnetrygd(
                        ident = "123",
                        ar = 2020
                    ).also {
                        assertEquals(
                            HentBarnetrygdResponse.Ok(
                                barnetrygdsaker = listOf(
                                    OmsorgsgrunnlagMelding.Sak(
                                        omsorgsyter = "12345678910",
                                        vedtaksperioder = listOf(
                                            OmsorgsgrunnlagMelding.VedtakPeriode(
                                                omsorgsmottaker = "09876543210",
                                                prosent = 100,
                                                fom = YearMonth.of(2020, Month.JANUARY),
                                                tom = YearMonth.of(2025, Month.DECEMBER)
                                            )
                                        )
                                    )
                                ),
                                rådataFraKilde = RådataFraKilde(
                                    """
                            {
                                "fagsaker": [
                                    {
                                        "fagsakId":"1",
                                        "fagsakEiersIdent":"12345678910",
                                        "barnetrygdPerioder":[
                                            {
                                                "personIdent":"09876543210",
                                                "delingsprosentYtelse":100,
                                                "ytelseTypeEkstern":"ORDINÆR_BARNETRYGD",
                                                "utbetaltPerMnd":2000,
                                                "stønadFom": "2020-01",
                                                "stønadTom": "2025-12"                                            
                                            }                                                                                          
                                        ]
                                    }
                                ]
                            }
                        """.trimIndent()
                                )
                            ), it
                        )
                    }
                }
            }
        }

        @Test
        fun `returner feil med diverse informasjon dersom kall til hent-barnetrygd svarer med noe annet enn 200`() {
            Mdc.scopedMdc(CorrelationId.generate()) {
                Mdc.scopedMdc(InnlesingId.generate()) {
                    wiremock.`hent-barnetrygd internal server error`()

                    client.hentBarnetrygd(
                        ident = "123",
                        ar = 2020
                    ).also {
                        assertEquals(
                            HentBarnetrygdResponse.Feil(
                                500,
                                """
                                    [
                                        {
                                           "status":"FUNKSJONELL_FEIL",
                                           "melding":"Dette gikk ikke så bra"
                                        }
                                    ]
                            """.trimIndent()
                            ), it
                        )
                    }
                }
            }
        }

        @Test
        fun `returner feil dersom kall til hent-barnetrygd svarer med 200 ok med tom liste`() {
            Mdc.scopedMdc(CorrelationId.generate()) {
                Mdc.scopedMdc(InnlesingId.generate()) {
                    wiremock.`hent-barnetrygd ok uten fagsaker`()

                    client.hentBarnetrygd(
                        ident = "123",
                        ar = 2020
                    ).also {
                        assertEquals(
                            HentBarnetrygdResponse.Feil(
                                200,
                                "Liste med barnetrygdsaker er tom"
                            ), it
                        )
                    }
                }
            }
        }

        @Test
        fun `returner feil dersom kall til hent-barnetrygd svarer med 200 ok og saker mangler barnetrygdperioder`() {
            Mdc.scopedMdc(CorrelationId.generate()) {
                Mdc.scopedMdc(InnlesingId.generate()) {
                    wiremock.`hent-barnetrygd ok uten barnetrygdperioder`()

                    client.hentBarnetrygd(
                        ident = "123",
                        ar = 2020
                    ).also {
                        assertEquals(
                            HentBarnetrygdResponse.Feil(
                                200,
                                "En eller flere av barnetrygdsakene mangler perioder"
                            ), it
                        )
                    }
                }
            }
        }
    }
}