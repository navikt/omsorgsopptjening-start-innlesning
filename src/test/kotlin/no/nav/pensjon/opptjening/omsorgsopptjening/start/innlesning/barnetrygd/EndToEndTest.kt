package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.deserialize
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.OmsorgsgrunnlagMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.`bestill-personer-med-barnetrygd accepted`
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.`hent-barnetrygd ok`
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.kafka.BarnetrygdmottakerKafkaMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.web.BarnetrygdWebApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import java.time.Month
import java.time.YearMonth
import java.util.UUID

class EndToEndTest : SpringContextTest.WithKafka() {

    @Autowired
    private lateinit var listener: OmsorgsopptjeningTopicListener

    @Autowired
    private lateinit var webApi: BarnetrygdWebApi

    companion object {
        @RegisterExtension
        private val wiremock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
            .build()!!
    }

    @Test
    fun `leser melding fra barnetryd topic, prosesserer meldingen og sender ny melding til intern topic`() {
        wiremock.`bestill-personer-med-barnetrygd accepted`()
        wiremock.`hent-barnetrygd ok`()

        val innlesingId = webApi.startInnlesning(2020).body!!

        sendStartInnlesingKafka(innlesingId)
        sendBarnetrygdmottakerDataKafka(
            melding = BarnetrygdmottakerKafkaMelding(
                meldingstype = BarnetrygdmottakerKafkaMelding.Type.DATA,
                requestId = UUID.fromString(innlesingId),
                personident = "12345678910"
            )
        )
        sendSluttInnlesingKafka(innlesingId)

        listener.removeFirstRecord(3).let { consumerRecord ->
            assertEquals(
                """
                    {"ident":"12345678910"}
                """.trimIndent(),
                consumerRecord.key()
            )
            deserialize<OmsorgsgrunnlagMelding>(consumerRecord.value()).also {
                assertEquals("12345678910", it.omsorgsyter)
                assertEquals("BARNETRYGD", it.omsorgstype.toString())
                assertEquals("BARNETRYGD", it.kilde.toString())
                assertEquals(
                    listOf(
                        OmsorgsgrunnlagMelding.Sak(
                            omsorgsyter = "12345678910",
                            vedtaksperioder = listOf(
                                OmsorgsgrunnlagMelding.VedtakPeriode(
                                    fom = YearMonth.of(2020, Month.JANUARY),
                                    tom = YearMonth.of(2025, Month.DECEMBER),
                                    omsorgsmottaker = "09876543210",
                                    prosent = 100,
                                )
                            )
                        )
                    ),
                    it.saker
                )
                assertEquals(
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
                    """.trimIndent(),
                    it.rådata.toString()
                )
                assertEquals(innlesingId, it.innlesingId.toString())
                assertNotNull(it.correlationId) //opprettes internt
            }
        }
    }
}