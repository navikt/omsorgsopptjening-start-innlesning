package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Topics
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.serialize
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Application
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.kafka.BarnetrygdmottakerKafkaMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.kafka.BarnetrygdmottakerKafkaTopic
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.config.KafkaIntegrationTestConfig
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.databasecontainer.PostgresqlTestContainer
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

@DirtiesContext
sealed class SpringContextTest {

    companion object {
        const val PDL_PATH = "/graphql"
        const val WIREMOCK_PORT = 9991
    }

    @BeforeEach
    fun setup() {
        PostgresqlTestContainer.instance.removeDataFromDB()
    }

    @SpringBootTest(classes = [Application::class])
    @EnableMockOAuth2Server
    class NoKafka : SpringContextTest()

    @ActiveProfiles("kafkaIntegrationTest")
    @EmbeddedKafka(partitions = 1, topics = [BarnetrygdmottakerKafkaTopic.NAME, Topics.Omsorgsopptjening.NAME])
    @SpringBootTest(classes = [Application::class])
    @Import(KafkaIntegrationTestConfig::class)
    @EnableMockOAuth2Server
    class WithKafka : SpringContextTest() {

        @Autowired
        lateinit var kafkaProducer: KafkaTemplate<String, String>

        fun sendStartInnlesingKafka(
            requestId: String
        ) {
            val pr = ProducerRecord(
                BarnetrygdmottakerKafkaTopic.NAME,
                null,
                "",
                serialize(
                    BarnetrygdmottakerKafkaMelding(
                        meldingstype = BarnetrygdmottakerKafkaMelding.Type.START,
                        requestId = UUID.fromString(requestId),
                        personident = null,
                        antallIdenterTotalt = 1
                    )
                ),
            )
            kafkaProducer.send(pr).get()
        }

        fun sendBarnetrygdmottakerDataKafka(
            melding: BarnetrygdmottakerKafkaMelding,
        ) {
            val pr = ProducerRecord(
                BarnetrygdmottakerKafkaTopic.NAME,
                null,
                null,
                melding.personident,
                serialize(melding),
            )
            kafkaProducer.send(pr).get()
        }

        fun sendUgyldigMeldingKafka() {
            val pr = ProducerRecord(
                BarnetrygdmottakerKafkaTopic.NAME,
                null,
                null,
                "",
                """{"bogus":"format"}""",
            )
            kafkaProducer.send(pr).get()
        }

        fun sendSluttInnlesingKafka(
            requestId: String
        ) {
            val pr = ProducerRecord(
                BarnetrygdmottakerKafkaTopic.NAME,
                null,
                "",
                serialize(
                    BarnetrygdmottakerKafkaMelding(
                        meldingstype = BarnetrygdmottakerKafkaMelding.Type.SLUTT,
                        requestId = UUID.fromString(requestId),
                        personident = null,
                        antallIdenterTotalt = 1
                    )
                ),
            )
            kafkaProducer.send(pr).get()
        }

        fun sendMeldinger(meldinger: List<BarnetrygdmottakerKafkaMelding>) {
            meldinger
                .map { melding ->
                    ProducerRecord(
                        BarnetrygdmottakerKafkaTopic.NAME,
                        null,
                        "",
                        serialize(melding),
                    ).also {
                        kafkaProducer.send(it)
                    }
                }
        }
    }
}