package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd


import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.KafkaMessageType
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.platform.commons.logging.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component


@Component
@Profile("!no-kafka")
class ProducedMessageListener {

    private val records: MutableList<ConsumerRecord<String, String>> = mutableListOf()

    init {
        LoggerFactory.getLogger(this::class.java).error { "THIS IS MY $this" }
    }

    @KafkaListener(
        containerFactory = "consumerContainerFactory",
        idIsGroup = false,
        topics = ["todo-topic"],
        groupId = "todo-produced-messages-group"
    )
    private fun poll(record: ConsumerRecord<String, String>, ack: Acknowledgment) {
        records.add(record)
        ack.acknowledge()
    }

    fun removeFirstRecord(maxSeconds: Int): ConsumerRecord<String, String> {
        var secondsPassed = 0
        while (secondsPassed < maxSeconds && records.isEmpty()) {
            Thread.sleep(1000)
            secondsPassed++
        }
        return records.firstOrNull()
            ?.also { records.remove(it) }
            ?: throw RuntimeException("No messages of type:${KafkaMessageType.OMSORGSOPPTJENING} to consume")
    }
}