package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.deserialize
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.KafkaMessageType
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Topics
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.barnetrygd.Barnetrygdmelding
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.serialize
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.BarnetrygdClient
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.BarnetrygdClientResponse
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.Barnetrygdmottaker
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.BarnetrygdmottakerRepository
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InnlesningService(
    private val client: BarnetrygdClient,
    private val repository: BarnetrygdmottakerRepository,
    private val kafkaProducer: KafkaTemplate<String, String>,
) {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }

    fun initierSendingAvIdenter(ar: Int): BarnetrygdClientResponse {
        return client.initierSendingAvIdenter(ar)
    }
    @Transactional
    fun prosesserBarnetrygdmottakere() {
        repository.finnNesteUprosesserte()?.let { barnetrygdmottaker ->
            Mdc.scopedMdc(CorrelationId.name, barnetrygdmottaker.correlationId) {
                try {
                    log.info("Prosesserer barnetrygdmottaker med id:${barnetrygdmottaker.id}")
                    log.info("Henter detaljer")
                    val detaljer = client.hentBarnetrygdDetaljer(
                        ident = barnetrygdmottaker.ident!!,
                        ar = barnetrygdmottaker.ar!!,
                    )
                    when (detaljer) {
                        is BarnetrygdClientResponse.Feil -> {
                            barnetrygdmottaker.retry()
                        }

                        is BarnetrygdClientResponse.Ok -> {
                            log.info("Publiserer detaljer til topic:${Topics.Omsorgsopptjening.NAME}")
                            kafkaProducer.send(createKafkaMessage(barnetrygdmottaker, detaljer)).get()
                            barnetrygdmottaker.ferdig()
                            log.info("Prosessering fullført")
                        }
                    }
                    repository.save(barnetrygdmottaker)
                } catch (e: Throwable) {
                    log.error("Exception caught while processing id: ${barnetrygdmottaker.id}, cause: ${e.cause}")
                    barnetrygdmottaker.retry()
                    repository.save(barnetrygdmottaker)
                    throw e
                }
            }
        }
    }

    private fun createKafkaMessage(
        barnetrygdmottaker: Barnetrygdmottaker,
        detaljer: BarnetrygdClientResponse.Ok
    ): ProducerRecord<String, String> {
        return ProducerRecord(
            Topics.Omsorgsopptjening.NAME,
            null,
            serialize(
                Topics.Omsorgsopptjening.Key(
                    ident = barnetrygdmottaker.ident!!
                )
            ),
            serialize(
                Barnetrygdmelding(
                    ident = barnetrygdmottaker.ident!!,
                    list = deserialize(detaljer.body!!)
                )
            ),
            setOf(
                RecordHeader(KafkaMessageType.name, KafkaMessageType.BARNETRYGD.toString().toByteArray()),
                RecordHeader(CorrelationId.name, barnetrygdmottaker.correlationId.toByteArray())
            ),
        )
    }
}