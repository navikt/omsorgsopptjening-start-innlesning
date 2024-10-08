package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.kafka

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.deserialize
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdInnlesingException
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdmottakerMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdmottakerMessageHandler
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.metrics.Metrikker
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
@Profile("dev-gcp", "prod-gcp", "kafkaIntegrationTest")
class BarnetrygdmottakerKafkaListener(
    private val handler: BarnetrygdmottakerMessageHandler,
    private val metrikker: Metrikker,
) {
    companion object {
        private val log = LoggerFactory.getLogger(BarnetrygdmottakerKafkaListener::class.java)
        private val secureLog = LoggerFactory.getLogger("secure")
    }

    @KafkaListener(
        containerFactory = "listener",
        topics = ["\${BARNETRYGDMOTTAKERE_TOPIC}"],
        groupId = "\${BARNETRYGDMOTTAKERE_CONSUMER_GROUP}"
    )
    fun poll(
        consumerRecord: List<ConsumerRecord<String, String>>,
        acknowledgment: Acknowledgment
    ) {
        val kafkaMelding = try {
            metrikker.tellKafkaMeldingstype { consumerRecord.deserialiser() }
        } catch (ex: KafkaMeldingDeserialiseringException) {
            metrikker.målFeiletMelding {}
            log.error("Klarte ikke å deserialisere til kjent meldingsformat. Ignorerer melding")
            secureLog.error("Klarte ikke å deserialisere til kjent meldingsformat. Ignorerer melding", ex)
            throw ex
        }

        try {
            handler.handle(
                kafkaMelding.map {
                    it.toDomain(
                        correlationId = CorrelationId.generate(),
                        innlesingId = InnlesingId.fromString(it.requestId.toString())
                    )
                }
            )
            acknowledgment.acknowledge()
        } catch (ex: BarnetrygdInnlesingException.EksistererIkke) {
            log.info("Innlesing med id: ${ex.id} eksisterer ikke. Det mangler bestilling for innlesingen, eller innlesingen har blitt invalidert som følge av feil i overføring. Ignorerer påfølgende meldinger for denne bestillingen.")
            throw ex
        } catch (ex: BarnetrygdInnlesingException.UgyldigTistand) {
            log.info("Ugyldig tilstandsendring av innlesing: ${ex.id} for meldingstype: ${ex.meldingstype}. Invaliderer innsending.")
            throw InvalidateOnExceptionWrapper(listOf(ex.id), ex)
        } catch (ex: Throwable) {
            log.info("Ukjent feil ved prosessering av melding, exception: ${ex::class.simpleName}. Invaliderer melding dersom problemet vedvarer etter retries.")
            secureLog.info("Ukjent feil ved prosessering av melding, exception: ${ex::class.simpleName}. Invaliderer melding dersom problemet vedvarer etter retries.", ex)
            //TODO dersom vi leser fra to forskjellige innlesinger vil begge være med i listen her siden vi ikke helt vet hvilken som feilet - sjekker status i InnlesingInvalidatingRetryListener - finne en bedre løsning?
            throw InvalidateOnExceptionWrapper(kafkaMelding.groupBy { it.requestId.toString() }.keys.toList(), ex)
        }
    }

    private fun List<ConsumerRecord<String, String>>.deserialiser(): List<BarnetrygdmottakerKafkaMelding> {
        return map { it.deserialiser() }
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
                BarnetrygdmottakerMelding.Start(correlationId, innlesingId, antallIdenterTotalt)
            }

            BarnetrygdmottakerKafkaMelding.Type.DATA -> {
                BarnetrygdmottakerMelding.Data(personident!!, correlationId, innlesingId, antallIdenterTotalt)
            }

            BarnetrygdmottakerKafkaMelding.Type.SLUTT -> {
                BarnetrygdmottakerMelding.Slutt(correlationId, innlesingId, antallIdenterTotalt)
            }
        }
    }
}

class InvalidateOnExceptionWrapper(val id: List<String>, ex: Throwable) : RuntimeException(ex)
class KafkaMeldingDeserialiseringException(val consumerRecord: ConsumerRecord<String, String>, ex: Throwable) :
    RuntimeException(ex)