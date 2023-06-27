package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.start

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class KafkaListener(
    private val objectMapper: ObjectMapper,
    private val barnetrygdmottakerRepository: BarnetrygdmottakerRepository
) {
    @KafkaListener(
        containerFactory = "consumerContainerFactory",
        idIsGroup = false,
        topics = ["barnetrygd-identer-topic"],
        groupId = "omsorgsopptjening-start-innlesning"
    )
    fun poll(
        consumerRecord: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment
    ) {
        barnetrygdmottakerRepository.save(objectMapper.readValue<BarnetrygdMottakerMelding>(consumerRecord.value()).toDomain())
        acknowledgment.acknowledge()
    }

    companion object {
        private val SECURE_LOG = LoggerFactory.getLogger("secure")
    }

    data class BarnetrygdMottakerMelding(val ident: String, val ar: Int){
        fun toDomain(): Barnetrygdmottaker  {
            return Barnetrygdmottaker(ident = ident, ar = ar)
        }
    }
}