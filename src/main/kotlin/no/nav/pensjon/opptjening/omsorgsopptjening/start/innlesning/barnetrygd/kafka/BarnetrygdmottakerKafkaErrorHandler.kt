package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.kafka

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdInnlesingException
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdInnlesingRepository
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.RetryListener
import org.springframework.stereotype.Component
import org.springframework.util.backoff.BackOff
import java.util.UUID

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
    private val innlesingRepository: BarnetrygdInnlesingRepository
) : RetryListener {

    private val invalidated: MutableList<String> = mutableListOf()

    companion object {
        private val log: Logger = LoggerFactory.getLogger(InnlesingInvalidatingRetryListener::class.java)
    }

    override fun failedDelivery(record: ConsumerRecord<*, *>, ex: Exception, deliveryAttempt: Int) {}

    override fun recovered(record: ConsumerRecord<*, *>, ex: java.lang.Exception) {
        ex.cause?.also { throwable ->
            when (throwable) {
                is InvalidateOnExceptionWrapper -> {
                    if (!invalidated.contains(throwable.id)) {
                        log.info("Invalidating (deleting all related data) innlesing with id: ${throwable.id} due to all records not being processed successfully.")
                        innlesingRepository.invalider(UUID.fromString(throwable.id))
                            .also { invalidated.add(throwable.id) }
                        log.info("Invalidated id: ${throwable.id}")
                    }
                }

                else -> {
                    //NOOP
                }
            }
        }
    }
}