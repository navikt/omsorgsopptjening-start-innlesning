package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.kafka

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.deserialize
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Mdc
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdmottakerMessageHandler
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdmottakerMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdInnlesingException
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
@Profile("dev-gcp", "prod-gcp", "kafkaIntegrationTest")
class BarnetrygdmottakerKafkaListener(
    private val handler: BarnetrygdmottakerMessageHandler
) {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }

    @KafkaListener(
        containerFactory = "listener",
        topics = [BarnetrygdmottakerKafkaTopic.NAME],
        groupId = "omsorgsopptjening-start-innlesning"
    )
    fun poll(
        consumerRecord: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment
    ) {
        val kafkaMelding = try {
            consumerRecord.deserialiser()
        } catch (ex: KafkaMeldingDeserialiseringException) {
            log.info("Klarte ikke å deserialisere til kjent meldingsformat. Ignorerer melding ${ex.consumerRecord}, exception: $ex")
            throw ex
        }

        Mdc.scopedMdc(CorrelationId.generate()) { correlationId ->
            Mdc.scopedMdc(InnlesingId.fromString(kafkaMelding.requestId.toString())) { innlesingId ->
                try {
                    handler.handle(consumerRecord.deserialiser().toDomain(correlationId, innlesingId))
                } catch (ex: BarnetrygdInnlesingException.EksistererIkke) {
                    log.info("Innlesing med id: ${ex.id} eksisterer ikke. Det mangler bestilling for innlesingen, eller innlesingen har blitt invalidert som følge av feil i overføring. Ignorerer påfølgende meldinger for denne bestillingen.")
                    throw ex
                } catch (ex: BarnetrygdInnlesingException.UgyldigTistand) {
                    log.info("Ugyldig tilstandsendring av innlesing: ${ex.id} for meldingstype: ${ex.meldingstype}. Invaliderer innsending.")
                    throw InvalidateOnExceptionWrapper(ex.id, ex)
                } catch (ex: Throwable) {
                    log.info("Ukjent feil ved prosessering av melding $kafkaMelding, exception: $ex. Invaliderer melding dersom problemet vedvarer etter retries.")
                    throw InvalidateOnExceptionWrapper(innlesingId.toString(), ex)
                }
            }
        }
    }

    private fun ConsumerRecord<String, String>.deserialiser(): BarnetrygdmottakerKafkaMelding {
        return try {
            deserialize<BarnetrygdmottakerKafkaMelding>(this.value())
        } catch (ex: Throwable) {
            throw KafkaMeldingDeserialiseringException(this, ex)
        }
    }

    private fun BarnetrygdmottakerKafkaMelding.toDomain(
        correlationId: CorrelationId,
        innlesingId: InnlesingId,
    ): BarnetrygdmottakerMelding {
        return when (meldingstype) {
            BarnetrygdmottakerKafkaMelding.Type.START -> {
                BarnetrygdmottakerMelding.Start(correlationId, innlesingId)
            }

            BarnetrygdmottakerKafkaMelding.Type.DATA -> {
                BarnetrygdmottakerMelding.Data(personident!!, correlationId, innlesingId)
            }

            BarnetrygdmottakerKafkaMelding.Type.SLUTT -> {
                BarnetrygdmottakerMelding.Slutt(correlationId, innlesingId)
            }
        }
    }
}


class InvalidateOnExceptionWrapper(val id: String, ex: Throwable) : RuntimeException(ex)
class KafkaMeldingDeserialiseringException(val consumerRecord: ConsumerRecord<String, String>, ex: Throwable) :
    RuntimeException(ex)