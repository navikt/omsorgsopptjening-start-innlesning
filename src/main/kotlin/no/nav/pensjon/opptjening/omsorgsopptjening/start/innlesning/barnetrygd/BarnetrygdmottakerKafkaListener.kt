package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Topics
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
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
        topics = [Topics.BARNETRYGDMOTTAKER],
        groupId = "omsorgsopptjening-start-innlesning"
    )
    fun poll(
        consumerRecord: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment
    ) {
        objectMapper.readValue<KafkaMelding>(consumerRecord.value()).let {
            log.info("Saving polled record for ident:${it.ident} and år:${it.ar}")
            barnetrygdmottakerRepository.save(it.toDomain(CorrelationId.generate()))
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