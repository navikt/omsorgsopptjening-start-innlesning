package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.config

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.InnlesingException
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.InnlesingRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.InvalidateOnExceptionWrapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.RetryListener
import org.springframework.stereotype.Component
import org.springframework.util.backoff.BackOff
import java.util.UUID

@Component
class KafkaErrorHandler(
    innlesingRepository: InnlesingRepository,
    backOff: BackOff
) : DefaultErrorHandler(backOff) {
    init {
        this.setRetryListeners(InnlesingInvalidatingRetryListener(innlesingRepository))
        this.addNotRetryableExceptions(InnlesingException.EksistererIkke::class.java)
        this.addNotRetryableExceptions(InnlesingException.UgyldigTistand::class.java)
    }
}

class InnlesingInvalidatingRetryListener(
    private val innlesingRepository: InnlesingRepository
) : RetryListener {

    private val invalidated: MutableList<UUID> = mutableListOf()

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override fun failedDelivery(record: ConsumerRecord<*, *>, ex: Exception, deliveryAttempt: Int) {}

    override fun recovered(record: ConsumerRecord<*, *>, ex: java.lang.Exception) {
        log.error("Processing and retries failed for record: $record, ex: $ex")
        ex.cause?.also { throwable ->
            when (throwable) {
                is InvalidateOnExceptionWrapper -> {
                    if (!invalidated.contains(throwable.innlesingId)) {
                        log.info("Invalidating (deleting all related data) innlesing with id: ${throwable.innlesingId} due to all records not being processed successfully.")
                        innlesingRepository.invalider(throwable.innlesingId)
                            .also { invalidated.add(throwable.innlesingId) }
                        log.info("Invalidated id: ${throwable.innlesingId}")
                    }
                }

                else -> {
                    //NOOP
                }
            }
        }
        super.recovered(record, ex)
    }
}