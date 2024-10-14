package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.kafka

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdInnlesing
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdInnlesingException
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.InnlesingRepository
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.RetryListener
import org.springframework.stereotype.Component
import org.springframework.util.backoff.BackOff

@Component
class BarnetrygdmottakerKafkaErrorHandler(
    backOff: BackOff,
    retryListener: InnlesingInvalidatingRetryListener
) : DefaultErrorHandler(backOff) {
    init {
        this.setRetryListeners(retryListener)
        this.addNotRetryableExceptions(BarnetrygdInnlesingException.EksistererIkke::class.java)
        this.addNotRetryableExceptions(BarnetrygdInnlesingException.UgyldigTistand::class.java)
        this.addNotRetryableExceptions(KafkaMeldingDeserialiseringException::class.java)
    }
}

@Component
class InnlesingInvalidatingRetryListener(
    private val innlesingRepository: InnlesingRepository
) : RetryListener {

    private val invalidated: MutableList<String> = mutableListOf()

    companion object {
        private val log: Logger = LoggerFactory.getLogger(InnlesingInvalidatingRetryListener::class.java)
    }

    override fun failedDelivery(record: ConsumerRecord<*, *>, ex: java.lang.Exception, deliveryAttempt: Int) {}

    override fun recovered(record: ConsumerRecords<*, *>, ex: java.lang.Exception) {
        ex.cause?.also { throwable ->
            when (throwable) {
                is InvalidateOnExceptionWrapper -> {
                    throwable.id.forEach {
                        if (!invalidated.contains(it)) {
                            innlesingRepository.finn(it).let { innlesing ->
                                when (innlesing) {
                                    is BarnetrygdInnlesing.Ferdig -> {
                                        //noop
                                    }

                                    null -> {
                                        //noop
                                    }

                                    else -> {
                                        log.info("Invalidating (deleting all related data) innlesing with id: ${innlesing.id} due to all records not being processed successfully.")
                                        innlesingRepository.invalider(innlesing.id.toUUID())
                                            .also { invalidated.add(innlesing.id.toString()) }
                                        log.info("Invalidated id: ${innlesing.id}")
                                    }
                                }
                            }

                        }
                    }
                }

                else -> {
                    //NOOP
                }
            }
        }
    }
}