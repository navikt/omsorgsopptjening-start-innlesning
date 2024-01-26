package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd


import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Topics
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component


@Component
@Profile("kafkaIntegrationTest")
class OmsorgsopptjeningTopicListener {

    private val records: MutableList<ConsumerRecord<String, String>> = mutableListOf()

    @KafkaListener(
        containerFactory = "listener",
        topics = [Topics.Omsorgsopptjening.NAME],
        groupId = "test-omsorgsgrunnlag-listener"
    )
    private fun poll(
        record: List<ConsumerRecord<String, String>>,
        ack: Acknowledgment
    ) {
        records.addAll(record)
        ack.acknowledge()
    }

    fun size(): Int {
        return records.size
    }

    fun removeFirstRecord(maxSeconds: Int): ConsumerRecord<String, String> {
        var secondsPassed = 0
        while (secondsPassed < maxSeconds && records.isEmpty()) {
            Thread.sleep(1000)
            secondsPassed++
        }
        return records.firstOrNull()
            ?.also { records.remove(it) }
            ?: throw RuntimeException("No messages of type to consume")
    }
}