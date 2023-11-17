package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.kafka

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.config.KafkaConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties
import java.time.Duration

@EnableKafka
@Configuration
@Profile("dev-gcp", "prod-gcp", "kafkaIntegrationTest")
class BarnetrygdtmottakerKafkaListenerConfig(
    @Value("\${kafka.brokers}") private val aivenBootstrapServers: String,
    private val customErrorHandler: BarnetrygdmottakerKafkaErrorHandler
) {

    @Bean("listener")
    fun listener(securityConfig: KafkaConfig.SecurityConfig): ConcurrentKafkaListenerContainerFactory<String, String>? =
        ConcurrentKafkaListenerContainerFactory<String, String>().apply {
            containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
            containerProperties.setAuthExceptionRetryInterval(Duration.ofSeconds(4L))
            consumerFactory = DefaultKafkaConsumerFactory(
                consumerConfig() + securityConfig,
                StringDeserializer(),
                StringDeserializer()
            )
            setCommonErrorHandler(customErrorHandler)
            isBatchListener = true
        }

    private fun consumerConfig(): Map<String, Any> = mapOf(
        ConsumerConfig.CLIENT_ID_CONFIG to "omsorgsopptjening-start-innlesning",
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to aivenBootstrapServers,
        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
        ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 1000,
    )
}