package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.deserialize
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.InnlesingRepository
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
    private val innlesingRepository: InnlesingRepository,
    private val barnetrygdmottakerRepository: BarnetrygdmottakerRepository
) {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }

    @KafkaListener(
        containerFactory = "listener",
        topics = [BarnetrygdTopic.NAME],
        groupId = "omsorgsopptjening-start-innlesning"
    )
    fun poll(
        consumerRecord: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment
    ) {
        val kafkaMelding = try {
            deserialize<KafkaMelding>(consumerRecord.value())
        } catch (ex: Throwable) {
            throw UkjentKafkaMeldingException(consumerRecord, ex)
        }

        try {
            Mdc.scopedMdc(CorrelationId.generate()) { correlationId ->
                Mdc.scopedMdc(InnlesingId.fromString(kafkaMelding.requestId.toString())) { innlesingId ->
                    when (kafkaMelding.meldingstype) {
                        KafkaMelding.Type.START -> {
                            log.info("Starter ny innlesing, id: $innlesingId")
                            innlesingRepository.start(innlesingId.toString())
                        }

                        KafkaMelding.Type.DATA -> {
                            log.info("Mottatt melding om barnetrygdmottaker")
                            barnetrygdmottakerRepository.save(
                                kafkaMelding.toBarnetrygdmottaker(
                                    correlationId = correlationId,
                                    innlesingId = innlesingId
                                )
                            )
                            log.info("Melding prosessert")
                        }

                        KafkaMelding.Type.SLUTT -> {
                            log.info("Fullført innlesing, id: $innlesingId")
                            innlesingRepository.fullført(innlesingId.toString())
                        }
                    }
                }
            }
            acknowledgment.acknowledge()
        } catch (ex: Throwable) {
            throw InvalidateInnlesingException(kafkaMelding.requestId, ex)
        }
    }
}


class InvalidateInnlesingException(val innlesingId: UUID, ex: Throwable) : RuntimeException(ex)
class UkjentKafkaMeldingException(val consumerRecord: ConsumerRecord<String, String>, ex: Throwable) :
    RuntimeException(ex)

data class KafkaMelding(
    val meldingstype: Type,
    val requestId: UUID,
    val personident: String?
) {
    fun toBarnetrygdmottaker(
        correlationId: CorrelationId,
        innlesingId: InnlesingId,
    ): Barnetrygdmottaker {
        return Barnetrygdmottaker(
            ident = personident!!,
            correlationId = correlationId,
            innlesingId = innlesingId
        )
    }


    enum class Type {
        START,
        DATA,
        SLUTT;
    }
}