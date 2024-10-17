package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Topics
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.serialize
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Application
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.kafka.BarnetrygdmottakerKafkaMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.kafka.BarnetrygdmottakerKafkaTopic
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.config.KafkaIntegrationTestConfig
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.databasecontainer.PostgresqlTestContainer
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.hjelpestønad.resetHjelpestønadSequence
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.net.Socket
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed class SpringContextTest {
    companion object {
        const val PDL_PATH = "/graphql"
        const val WIREMOCK_PORT = 9991
        const val READINESS_TOPIC = "readiness-topic"
    }

    @BeforeEach
    fun setup() {
        PostgresqlTestContainer.instance.removeDataFromDB()
        resetHjelpestønadSequence()
    }

    @SpringBootTest(classes = [Application::class])
    @EnableMockOAuth2Server
    class NoKafka : SpringContextTest()

    @ActiveProfiles("kafkaIntegrationTest")
    @EmbeddedKafka(
        partitions = 1,
        topics = [BarnetrygdmottakerKafkaTopic.NAME,
            Topics.Omsorgsopptjening.NAME,
            READINESS_TOPIC]
    )
    @SpringBootTest(classes = [Application::class])
    @Import(KafkaIntegrationTestConfig::class)
    @EnableMockOAuth2Server
    @DirtiesContext
    class WithKafka : SpringContextTest() {

        @Autowired
        lateinit var kafkaProducer: KafkaTemplate<String, String>

        @Autowired
        lateinit var kafkaBroker: EmbeddedKafkaBroker

        fun ensureKafkaIsReady() {
            Thread.sleep(1000)
            val future = kafkaProducer.send(READINESS_TOPIC, "key", "msg")
            kafkaProducer.flush()
            future.get(10, TimeUnit.SECONDS)
            println("READINESS_TOPIC.length = ${READINESS_TOPIC.length}")
            println("KAFKA BROKER: $kafkaBroker")
        }

        fun awaitKafkaBroker(timeoutSeconds: Int = 60) {
            var attempts = 0
            val maxAttempts = timeoutSeconds * 10
            val brokerAddress = kafkaBroker.brokersAsString
            println("BrokerAddress: $brokerAddress")
            val host = brokerAddress.split(":").first()
            val port = brokerAddress.substringAfter(":").toInt()

            while (attempts < maxAttempts) {
                try {
                    Socket("localhost", port).use {
                        println("awaitKafkaBroker: Kafka is ready (attempts=$attempts)")
                        return
                    }
                } catch (e: Exception) {
                    Thread.sleep(100)
                }
                attempts++
            }
            throw RuntimeException("awaitKafkaBroker: Kafka broker did not start without $timeoutSeconds seconds")
        }


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