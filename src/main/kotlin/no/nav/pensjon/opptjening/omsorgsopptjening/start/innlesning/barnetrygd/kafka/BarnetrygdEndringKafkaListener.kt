package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.kafka

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.deserialize
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdEndring
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdEndringMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Personidentifikator
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdmottakerRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.metrics.Metrikker
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
@Profile("dev-gcp", "prod-gcp", "kafkaIntegrationTest")
class BarnetrygdEndringKafkaListener(
    private val handler: BarnetrygdmottakerRepository,
    private val metrikker: Metrikker,
) {
    companion object {
        private val log = LoggerFactory.getLogger(BarnetrygdmottakerKafkaListener::class.java)
        private val secureLog = LoggerFactory.getLogger("secure")
    }

    @KafkaListener(
        containerFactory = "listener",
        topics = [$$"${BARNETRYGD_ENDRING_TOPIC}"],
        groupId = $$"${BARNETRYGD_ENDRING_CONSUMER_GROUP}"
    )
    fun poll(
        consumerRecord: List<ConsumerRecord<String, String>>,
        acknowledgment: Acknowledgment
    ) {
        val kafkaMeldinger = consumerRecord
            .mapNotNull { record -> record.deserialiser() }
            .also { metrikker.tellEndringer(it) }

        handler.lagre(kafkaMeldinger)

        acknowledgment.acknowledge()
    }

    private fun ConsumerRecord<String, String>.deserialiser(): BarnetrygdEndring? {
        return try {
            deserialize<BarnetrygdEndringMelding>(this.value()).toDomain()
        } catch (ex: Throwable) {
            log.error("Ukjent meldingsformat eller ugyldig innhold, ignorerer melding med offset: ${this.offset()}")
            secureLog.error("Ukjent meldingsformat eller ugyldig innhold, ignorerer melding: $this", ex)
            null
        }
    }

    private fun BarnetrygdEndringMelding.toDomain(): BarnetrygdEndring {
        return BarnetrygdEndring(
            id = null,
            ident = Personidentifikator.krev(ident),
            vedtakstidspunkt = vedtakstidspunkt,
            endringstidspunkt = endringstidspunkt,
            sekvensnummer = sekvensnummer,
            opprettet = null,
        )
    }
}