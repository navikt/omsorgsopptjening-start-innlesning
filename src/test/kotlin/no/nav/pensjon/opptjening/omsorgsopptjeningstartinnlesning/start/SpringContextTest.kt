package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.start

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.App
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles


sealed class SpringContextTest {

    companion object {
        const val PDL_PATH = "/graphql"
        const val WIREMOCK_PORT = 9991
    }

    @ActiveProfiles("no-kafka")
    @SpringBootTest(classes = [App::class])
    @EnableMockOAuth2Server
    class NoKafka : SpringContextTest() {

    }

    @EmbeddedKafka(partitions = 1, topics = ["barnetrygd-identer-topic","todo-topic"])
    @SpringBootTest(classes = [App::class])
    @Import(KafkaIntegrationTestConfig::class)
    @EnableMockOAuth2Server
    class WithKafka : SpringContextTest() {

        @Autowired
        lateinit var kafkaProducer: KafkaTemplate<String, String>

        @Autowired
        lateinit var objectMapper: ObjectMapper

        fun sendBarnetrygdMottakerKafka(
            melding: BarnetrygdmottakerKafkaListener.BarnetrygdMottakerMelding
        ) {
            val omsorgsArbeid = objectMapper.writeValueAsString(melding)

            val pr = ProducerRecord(
                "barnetrygd-identer-topic",
                null,
                null,
                melding.ident,
                omsorgsArbeid
            )
            kafkaProducer.send(pr).get()
        }
    }
}