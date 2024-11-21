package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning

import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.deserialize
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Kilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Landstilknytning
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Omsorgstype
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.PersongrunnlagMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.OmsorgsopptjeningTopicListener
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.SpringContextTest
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdmottakerService
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.SendTilBestemService
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.kafka.BarnetrygdmottakerKafkaMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.web.BarnetrygdWebApi
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.barnetrygd.`bestill-personer-med-barnetrygd accepted`
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.barnetrygd.`hent-barnetrygd ok`
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.hjelpestønad.`hent hjelpestønad ok - har hjelpestønad`
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.pdl.`pdl fnr fra query`
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.skyscreamer.jsonassert.JSONAssert
import org.springframework.beans.factory.annotation.Autowired
import java.time.Month
import java.time.YearMonth
import java.util.*

class EndToEndTest : SpringContextTest.WithKafka() {

    @Autowired
    private lateinit var listener: OmsorgsopptjeningTopicListener

    @Autowired
    private lateinit var webApi: BarnetrygdWebApi

    @Autowired
    private lateinit var barnetrygdmottakerService: BarnetrygdmottakerService

    @Autowired
    private lateinit var sendTilBestemService: SendTilBestemService

    companion object {
        @JvmField
        @RegisterExtension
        val wiremock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
            .build()!!
    }

    @Test
    fun `leser melding fra barnetrygd topic, prosesserer meldingen og sender ny melding til intern topic`() {

        wiremock.`pdl fnr fra query`()
        wiremock.`bestill-personer-med-barnetrygd accepted`()
        wiremock.`hent-barnetrygd ok`()
        wiremock.`hent hjelpestønad ok - har hjelpestønad`()

        val innlesingId = UUID.fromString(webApi.startInnlesning(2020).body!!)

        sendMeldinger(
            listOf(
                BarnetrygdmottakerKafkaMelding(
                    meldingstype = BarnetrygdmottakerKafkaMelding.Type.START,
                    requestId = innlesingId,
                    personident = null,
                    antallIdenterTotalt = 1
                ),
                BarnetrygdmottakerKafkaMelding(
                    meldingstype = BarnetrygdmottakerKafkaMelding.Type.DATA,
                    requestId = innlesingId,
                    personident = "12345678910",
                    antallIdenterTotalt = 1,
                ),
                BarnetrygdmottakerKafkaMelding(
                    meldingstype = BarnetrygdmottakerKafkaMelding.Type.SLUTT,
                    requestId = innlesingId,
                    personident = null,
                    antallIdenterTotalt = 1
                )
            )
        )

        Thread.sleep(2000)
        barnetrygdmottakerService.låsForBehandling().forEach {
            barnetrygdmottakerService.prosesserOgFrigi(it)
        }
        sendTilBestemService.sendTilBestem()

        listener.removeFirstRecord(3).let { consumerRecord ->
            assertThatJson(consumerRecord.key()).isEqualTo(
                """
                    {"ident":"12345678910"}
                """.trimIndent()
            )

            deserialize<PersongrunnlagMelding>(consumerRecord.value()).also {
                assertThat(it.omsorgsyter).isEqualTo("12345678910")
                assertThat(
                    it.persongrunnlag
                ).isEqualTo(
                    listOf(
                        PersongrunnlagMelding.Persongrunnlag.of(
                            omsorgsyter = "12345678910",
                            omsorgsperioder = listOf(
                                PersongrunnlagMelding.Omsorgsperiode(
                                    fom = YearMonth.of(2020, Month.JANUARY),
                                    tom = YearMonth.of(2021, Month.DECEMBER),
                                    omsorgsmottaker = "09876543210",
                                    omsorgstype = Omsorgstype.FULL_BARNETRYGD,
                                    kilde = Kilde.BARNETRYGD,
                                    utbetalt = 2000,
                                    landstilknytning = Landstilknytning.NORGE,
                                ),
                            ),
                            hjelpestønadsperioder = listOf(
                                PersongrunnlagMelding.Hjelpestønadperiode(
                                    fom = YearMonth.of(2020, Month.JANUARY),
                                    tom = YearMonth.of(2021, Month.DECEMBER),
                                    omsorgsmottaker = "09876543210",
                                    omsorgstype = Omsorgstype.HJELPESTØNAD_FORHØYET_SATS_3,
                                    kilde = Kilde.INFOTRYGD,
                                )
                            ),
                        )
                    ),
                )

                assertThat(it.rådata[0]["fnr"]).isEqualTo("12345678910")
                assertThat(it.rådata[0]["fom"]).isEqualTo("2020-01-01")
                assertThatJson(it.rådata[0]["barnetrygd"] as String)
                    .`when`(Option.IGNORING_EXTRA_FIELDS)
                    .isEqualTo(
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
                    """.trimIndent(),
                    )
                assertThat(it.rådata[1]["fnr"]).isEqualTo("09876543210")
                assertThat(it.rådata[1]["fom"]).isEqualTo("2020-01-01")
                assertThat(it.rådata[1]["tom"]).isEqualTo("2021-12-31")
                JSONAssert.assertEquals(
                    """
                        [
                            {
                                "id":"101",
                                "ident":"09876543210",
                                "fom":"2020-01",
                                "tom":"2025-12",
                                "omsorgstype":"FORHØYET_SATS_3"
                            }
                        ]
                    """.trimIndent(),
                    it.rådata[1]["hjelpestønad"] as String,
                    false,
                )
                assertThat(it.innlesingId.toString()).isEqualTo(innlesingId.toString())
                assertThat(it.correlationId).isNotNull() //opprettes internt
            }
        }
    }
}