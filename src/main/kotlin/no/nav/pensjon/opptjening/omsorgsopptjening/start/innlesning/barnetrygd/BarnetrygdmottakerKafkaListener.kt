package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.deserialize
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Topics
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Mdc
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
@Profile("dev-gcp", "prod-gcp", "kafkaIntegrationTest")
class BarnetrygdmottakerKafkaListener(
    private val repo: BarnetrygdmottakerRepository
) {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }

    @KafkaListener(
        containerFactory = "listener",
        topics = [Topics.BARNETRYGDMOTTAKER],
        groupId = "omsorgsopptjening-start-innlesning"
    )
    fun poll(
        consumerRecord: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment
    ) {
        Mdc.scopedMdc(CorrelationId.name, CorrelationId.generate()) {0
            deserialize<KafkaMelding>(consumerRecord.value()).let {
                log.info("Mottatt melding om barnetrygdmottaker")
                repo.save(it.toDomain(Mdc.getOrCreateCorrelationId()))
                log.info("Melding prosessert")
            }
        }
        acknowledgment.acknowledge()
    }

    data class KafkaMelding(
        val ident: String,
        val ar: Int,
    ) {
        fun toDomain(correlationId: String): Barnetrygdmottaker {
            return Barnetrygdmottaker(
                ident = ident,
                år = ar,
                correlationId = correlationId
            )
        }
    }
}