package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Topics
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.serialize
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Application
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
        const val WIREMOCK_PORT = 9991
    }

    @BeforeEach
    fun setup() {
        PostgresqlTestContainer.instance.removeDataFromDB()
    }

    @SpringBootTest(classes = [Application::class])
    @EnableMockOAuth2Server
    class NoKafka : SpringContextTest() {

    }

    @ActiveProfiles("kafkaIntegrationTest")
    @EmbeddedKafka(partitions = 1, topics = [Topics.BARNETRYGDMOTTAKER, Topics.Omsorgsopptjening.NAME])
    @SpringBootTest(classes = [Application::class])
    @Import(KafkaIntegrationTestConfig::class)
    @EnableMockOAuth2Server
    class WithKafka : SpringContextTest() {

        @Autowired
        lateinit var kafkaProducer: KafkaTemplate<String, String>

        @Autowired
        lateinit var objectMapper: ObjectMapper
        fun sendStartInnlesingKafka(
            requestId: String
        ) {
            val pr = ProducerRecord(
                Topics.BARNETRYGDMOTTAKER,
                null,
                "",
                serialize(
                    KafkaMelding(
                        meldingstype = KafkaMelding.Type.START,
                        requestId = UUID.fromString(requestId),
                        personident = null
                    )
                ),
            )
            kafkaProducer.send(pr).get()
        }

        fun sendBarnetrygdmottakerDataKafka(
            melding: KafkaMelding,
        ) {
            val pr = ProducerRecord(
                Topics.BARNETRYGDMOTTAKER,
                null,
                null,
                melding.personident,
                serialize(melding),
            )
            kafkaProducer.send(pr).get()
        }

        fun sendSluttInnlesingKafka(
            requestId: String
        ) {
            val pr = ProducerRecord(
                Topics.BARNETRYGDMOTTAKER,
                null,
                "",
                serialize(
                    KafkaMelding(
                        meldingstype = KafkaMelding.Type.SLUTT,
                        requestId = UUID.fromString(requestId),
                        personident = null
                    )
                ),
            )
            kafkaProducer.send(pr).get()
        }
    }
}