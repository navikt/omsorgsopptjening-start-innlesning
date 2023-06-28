package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.start

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.juli.logging.Log
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Profile("!no-kafka")

@Component
class BarnetrygdmottakerKafkaListener(
    private val objectMapper: ObjectMapper,
    private val barnetrygdmottakerRepository: BarnetrygdmottakerRepository
) {
    companion object {
        val log = LoggerFactory.getLogger(this::class.java)
    }
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
        objectMapper.readValue<BarnetrygdMottakerMelding>(consumerRecord.value()).let {
            log.info("Saving polled record for ident:${it.ident} and år:${it.ar}")
            barnetrygdmottakerRepository.save(it.toDomain())
        }
        acknowledgment.acknowledge()
    }

    data class BarnetrygdMottakerMelding(val ident: String, val ar: Int) {
        fun toDomain(): Barnetrygdmottaker {
            return Barnetrygdmottaker(ident = ident, år = ar)
        }
    }
}