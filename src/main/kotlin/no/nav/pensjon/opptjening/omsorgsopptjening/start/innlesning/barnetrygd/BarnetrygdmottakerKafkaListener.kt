package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.deserialize
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Topics
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.InnlesingRepo
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Mdc
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.util.UUID

@Component
@Profile("dev-gcp", "prod-gcp", "kafkaIntegrationTest")
class BarnetrygdmottakerKafkaListener(
    private val innlesingRepo: InnlesingRepo,
    private val barnetrygdmottakerRepository: BarnetrygdmottakerRepository
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
        deserialize<KafkaMelding>(consumerRecord.value())
            .also { kafkaMelding ->
                Mdc.scopedMdc(CorrelationId.name, CorrelationId.generate()) { correlationId ->
                    kafkaMelding.requestId.toString().also { requestId ->
                        when (kafkaMelding.meldingstype) {
                            KafkaMelding.Type.START -> {
                                log.info("Starter ny innlesing, id: $requestId")
                                innlesingRepo.start(requestId)
                            }

                            KafkaMelding.Type.DATA -> {
                                deserialize<KafkaMelding>(consumerRecord.value()).let {
                                    log.info("Mottatt melding om barnetrygdmottaker")
                                    barnetrygdmottakerRepository.save(
                                        it.toBarnetrygdmottaker(
                                            correlationId = UUID.fromString(correlationId),
                                            requestId = requestId
                                        )
                                    )
                                    log.info("Melding prosessert")
                                }
                            }

                            KafkaMelding.Type.SLUTT -> {
                                log.info("Fullført innlesing, id: $requestId")
                                innlesingRepo.fullført(requestId)
                            }
                        }
                    }
                }
            }
        acknowledgment.acknowledge()
    }
}

data class KafkaMelding(
    val meldingstype: Type,
    val requestId: UUID,
    val personident: String?
) {
    fun toBarnetrygdmottaker(
        correlationId: UUID,
        requestId: String,
    ): Barnetrygdmottaker {
        return Barnetrygdmottaker(
            ident = personident!!,
            correlationId = correlationId,
            requestId = requestId
        )
    }


    enum class Type {
        START,
        DATA,
        SLUTT;
    }
}