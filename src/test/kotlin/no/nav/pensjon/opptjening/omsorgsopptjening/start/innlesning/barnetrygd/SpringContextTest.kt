package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Topics
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
    @SpringBootTest(classes = [no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.App::class])
    @EnableMockOAuth2Server
    class NoKafka : SpringContextTest() {

    }

    @EmbeddedKafka(partitions = 1, topics = [Topics.BARNETRYGDMOTTAKER, Topics.Omsorgsopptjening.NAME])
    @SpringBootTest(classes = [no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.App::class])
    @Import(KafkaIntegrationTestConfig::class)
    @EnableMockOAuth2Server
    class WithKafka : SpringContextTest() {

        @Autowired
        lateinit var kafkaProducer: KafkaTemplate<String, String>

        @Autowired
        lateinit var objectMapper: ObjectMapper

        fun sendBarnetrygdMottakerKafka(
            melding: BarnetrygdmottakerKafkaListener.KafkaMelding
        ) {
            val omsorgsArbeid = objectMapper.writeValueAsString(melding)

            val pr = ProducerRecord(
                Topics.BARNETRYGDMOTTAKER,
                null,
                null,
                melding.ident,
                omsorgsArbeid
            )
            kafkaProducer.send(pr).get()
        }
    }
}