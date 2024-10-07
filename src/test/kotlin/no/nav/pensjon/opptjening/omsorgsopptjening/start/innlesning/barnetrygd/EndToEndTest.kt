package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.deserialize
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Kilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Landstilknytning
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Omsorgstype
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.PersongrunnlagMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.serialize
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.`bestill-personer-med-barnetrygd accepted`
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.`hent hjelpestønad ok - har hjelpestønad`
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.`hent-barnetrygd ok`
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.kafka.BarnetrygdmottakerKafkaMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.web.BarnetrygdWebApi
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.person.external.pdl.`pdl fnr ett i bruk`
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.skyscreamer.jsonassert.JSONAssert
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
        @JvmField
        @RegisterExtension
        val wiremock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
            .build()!!
    }

    @Test
    fun `leser melding fra barnetryd topic, prosesserer meldingen og sender ny melding til intern topic`() {
//        ensureKafkaIsReady()
//        awaitKafkaBroker(60)
//        // TODO: Denne sleep'en må til for at testen skal passere på kommandolinje hos meg (jan), men
//        // passerer andre steder. Skyldes antagelig at testene ikke er godt nok isolert.
//        println("*** BEFORE SLEEP: ${kafkaProducer.kafkaAdmin}")
//        Thread.sleep(5000)
//        println("*** AFTER SLEEP: ${kafkaProducer.kafkaAdmin}")
//        assertThat(listener.size()).isZero()

        wiremock.`pdl fnr ett i bruk`()
        wiremock.`bestill-personer-med-barnetrygd accepted`()
        wiremock.`hent-barnetrygd ok`()
        wiremock.`hent hjelpestønad ok - har hjelpestønad`()

        val innlesingId = webApi.startInnlesning(2020).body!!

        sendMeldinger(
            listOf(
                BarnetrygdmottakerKafkaMelding(
                    meldingstype = BarnetrygdmottakerKafkaMelding.Type.START,
                    requestId = UUID.fromString(innlesingId),
                    personident = null,
                    antallIdenterTotalt = 1
                ),
                BarnetrygdmottakerKafkaMelding(
                    meldingstype = BarnetrygdmottakerKafkaMelding.Type.DATA,
                    requestId = UUID.fromString(innlesingId),
                    personident = "12345678910",
                    antallIdenterTotalt = 1,
                ),
                BarnetrygdmottakerKafkaMelding(
                    meldingstype = BarnetrygdmottakerKafkaMelding.Type.SLUTT,
                    requestId = UUID.fromString(innlesingId),
                    personident = null,
                    antallIdenterTotalt = 1
                )
            )
        )

        Thread.sleep(2000)
//        assertThat(listener.size()).isOne()

        listener.removeFirstRecord(3).let { consumerRecord ->
            val expectedKey =
                """
                    {"ident":"12345678910"}
                """.trimIndent()
            JSONAssert.assertEquals(
                consumerRecord.key(),
                expectedKey,
                true
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
                val expectedRådata = """[{
                        "fnr":"12345678910",
                        "fom":"2020-01-01",
                        "barnetrygd":"{\n    \"fagsaker\": [\n        {\n            \"fagsakEiersIdent\":\"12345678910\",\n            \"barnetrygdPerioder\":[\n                {\n                    \"personIdent\":\"09876543210\",\n                    \"delingsprosentYtelse\":\"FULL\",\n                    \"ytelseTypeEkstern\":\"ORDINÆR_BARNETRYGD\",\n                    \"utbetaltPerMnd\":2000,\n                    \"stønadFom\": \"2020-01\",\n                    \"stønadTom\": \"2025-12\",\n                    \"sakstypeEkstern\":\"NASJONAL\",\n                    \"kildesystem\":\"BA\",\n                    \"pensjonstrygdet\":null,\n                    \"norgeErSekundærlandMedNullUtbetaling\":false\n                }\n            ]\n        }\n    ]\n}"},{"fnr":"09876543210","fom":"2020-01-01","tom":"2021-12-31","hjelpestønad":"[\n    {\n        \"id\":\"123\",\n        \"ident\":\"09876543210\",\n        \"fom\":\"2020-01\",\n        \"tom\":\"2025-12\",\n        \"omsorgstype\":\"FORHØYET_SATS_3\"\n    }\n]"
                        }]""".trimIndent()

                JSONAssert.assertEquals(
                    serialize(it.rådata),
                    expectedRådata,
                    false
                )
                assertThat(it.innlesingId.toString()).isEqualTo(innlesingId)
                assertThat(it.correlationId).isNotNull() //opprettes internt
            }
        }
    }
}