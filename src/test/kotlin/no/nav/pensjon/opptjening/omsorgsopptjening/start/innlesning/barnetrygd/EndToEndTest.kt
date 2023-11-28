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
        @JvmField
        @RegisterExtension
        val wiremock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
            .build()!!
    }

    @Test
    fun `leser melding fra barnetryd topic, prosesserer meldingen og sender ny melding til intern topic`() {
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

        listener.removeFirstRecord(3).let { consumerRecord ->
            assertEquals(
                """
                    {"ident":"12345678910"}
                """.trimIndent(),
                consumerRecord.key()
            )
            deserialize<PersongrunnlagMelding>(consumerRecord.value()).also {
                assertEquals("12345678910", it.omsorgsyter)
                assertEquals(
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
                    it.persongrunnlag
                )
                assertEquals(
                    """
                        [{"fom":"2020-01-01","barnetrygd":"{\n    \"fagsaker\": [\n        {\n            \"fagsakEiersIdent\":\"12345678910\",\n            \"barnetrygdPerioder\":[\n                {\n                    \"personIdent\":\"09876543210\",\n                    \"delingsprosentYtelse\":\"FULL\",\n                    \"ytelseTypeEkstern\":\"ORDINÆR_BARNETRYGD\",\n                    \"utbetaltPerMnd\":2000,\n                    \"stønadFom\": \"2020-01\",\n                    \"stønadTom\": \"2025-12\",\n                    \"sakstypeEkstern\":\"NASJONAL\",\n                    \"kildesystem\":\"BA\",\n                    \"pensjonstrygdet\":null,\n                    \"norgeErSekundærlandMedNullUtbetaling\":false\n                }\n            ]\n        }\n    ]\n}"},{"fom":"2020-01-01","tom":"2021-12-31","hjelpestønad":"[\n    {\n        \"id\":\"123\",\n        \"ident\":\"09876543210\",\n        \"fom\":\"2020-01\",\n        \"tom\":\"2025-12\",\n        \"omsorgstype\":\"FORHØYET_SATS_3\"\n    }\n]"}]
                    """.trimIndent(),
                    serialize(it.rådata)
                )
                assertEquals(innlesingId, it.innlesingId.toString())
                assertNotNull(it.correlationId) //opprettes internt
            }
        }
    }
}