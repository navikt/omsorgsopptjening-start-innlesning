package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Rådata
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Topics
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.PersongrunnlagMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.serialize
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Mdc
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdinformasjonRepository
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.lang.reflect.UndeclaredThrowableException
import java.util.*

@Service
class SendTilBestemService(
    private val barnetrygdinformasjonRepository: BarnetrygdinformasjonRepository,
    private val kafkaProducer: KafkaTemplate<String, String>,
    private val transactionTemplate: TransactionTemplate,
    @Value("\${OMSORGSOPPTJENING_TOPIC}") val omsorgsopptjeningTopic: String
) {
    companion object {
        private val log = LoggerFactory.getLogger(SendTilBestemService::class.java)
    }

    fun process(): List<Barnetrygdinformasjon>? {
        val låsteTilSending = transactionTemplate.execute {
            barnetrygdinformasjonRepository.finnNesteTilBehandling(10)
        }
        try {
            val barnetrygdinformasjonsListe: List<Barnetrygdinformasjon?>? =
                låsteTilSending?.data?.map { barnetrygdinformasjon ->
                    Mdc.scopedMdc(CorrelationId(barnetrygdinformasjon.correlationId)) {
                        Mdc.scopedMdc(InnlesingId(barnetrygdinformasjon.innlesingId)) {
                            send(barnetrygdinformasjon)
                        }
                    }
                }
            return barnetrygdinformasjonsListe?.filterNotNull()?.ifEmpty { null }
        } finally {
            låsteTilSending?.let { barnetrygdinformasjonRepository.frigi(it) }
        }
    }

    protected fun send(
        barnetrygdinformasjon: Barnetrygdinformasjon
    ): Barnetrygdinformasjon? {
        return try {
            log.info("Start prosessering")
            transactionTemplate.execute {

                sendToKafka(barnetrygdinformasjon)

                barnetrygdinformasjon.sendt().let {
                    barnetrygdinformasjonRepository.oppdaterStatus(it)
                    it
                }
            } // end transaction
        } catch (outerEx: Throwable) {
            val ex = when (outerEx) {
                is UndeclaredThrowableException -> outerEx.undeclaredThrowable
                else -> outerEx
            }
            log.warn("Fikk feil ved sending av barnetrygdinformasjon: ${ex::class.qualifiedName}")
            null
        }
    }

    private fun sendToKafka(barnetrygdinformasjon: Barnetrygdinformasjon) {
        kafkaProducer.send(
            createKafkaMessage(
                barnetrygdinformasjon
            )
        ).get()
    }

    private fun createKafkaMessage(
        barnetrygdinformasjon: Barnetrygdinformasjon,
    ): ProducerRecord<String, String> {
        return ProducerRecord(
            omsorgsopptjeningTopic,
            null,
            serialize(
                Topics.Omsorgsopptjening.Key(
                    ident = barnetrygdinformasjon.ident,
                )
            ),
            serialize(
                PersongrunnlagMelding(
                    omsorgsyter = barnetrygdinformasjon.ident,
                    persongrunnlag = barnetrygdinformasjon.persongrunnlag,
                    rådata = barnetrygdinformasjon.rådata,
                    innlesingId = InnlesingId(barnetrygdinformasjon.innlesingId),
                    correlationId = CorrelationId(barnetrygdinformasjon.correlationId),
                )
            )
        )
    }
}
