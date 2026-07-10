package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import java.util.UUID
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Topics
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.serialize
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Application
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.PersonIdService
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
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

sealed class SpringContextTest {
    companion object {
        const val PDL_PATH = "/graphql"
        const val WIREMOCK_PORT = 9991
        const val READINESS_TOPIC = "readiness-topic"

        // Poll i stedet for fast Thread.sleep: returnerer med en gang betingelsen er oppfylt, så en grønn
        // test venter aldri unødig. timeoutMs er kun et failsafe-tak som lar en reelt brutt test feile
        // fremfor å henge; verdien påvirker aldri en grønn kjøring, derfor én felles default her.
        fun await(timeoutMs: Long = 30_000, condition: () -> Boolean) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (condition()) return
                Thread.sleep(50)
            }
            error("Betingelsen ble ikke oppfylt innen ${timeoutMs}ms")
        }
    }

    @Autowired
    private lateinit var personIdService: PersonIdService

    @BeforeEach
    fun setup() {
        PostgresqlTestContainer.instance.removeDataFromDB()
        personIdService.clearCache()
        resetHjelpestønadSequence()
    }

    @SpringBootTest(classes = [Application::class])
    @EnableMockOAuth2Server
    class NoKafka : SpringContextTest() {
        // KafkaAutoConfiguration finnes ikke i Boot 4 (flyttet til eget modul) og appen konfigurerer
        // KafkaTemplate kun for kafka-profiler. NoKafka-tester trenger derfor en mock for at
        // SendTilBestemService skal kunne wires. Subklasser stubber den selv ved behov.
        @MockitoBean
        protected lateinit var kafkaTemplate: KafkaTemplate<String, String>
    }

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

        fun sendStartInnlesingKafka(
            requestId: String
        ) {
            val pr = ProducerRecord<String, String>(
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
            val pr = ProducerRecord<String, String>(
                BarnetrygdmottakerKafkaTopic.NAME,
                null,
                null,
                melding.personident,
                serialize(melding),
            )
            kafkaProducer.send(pr).get()
        }

        fun sendUgyldigMeldingKafka() {
            val pr = ProducerRecord<String, String>(
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
            val pr = ProducerRecord<String, String>(
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
                    ProducerRecord<String, String>(
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