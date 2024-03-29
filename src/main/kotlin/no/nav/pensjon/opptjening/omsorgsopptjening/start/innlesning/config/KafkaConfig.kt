package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.config

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import java.io.Serializable

@EnableKafka
@Configuration
@Profile("dev-gcp", "prod-gcp", "kafkaIntegrationTest")
class KafkaConfig(
    @Value("\${kafka.brokers}") private val aivenBootstrapServers: String
) {
    @Bean
    @Profile("dev-gcp", "prod-gcp")
    fun securityConfig(
        @Value("\${kafka.keystore.path}") keystorePath: String,
        @Value("\${kafka.credstore.password}") credstorePassword: String,
        @Value("\${kafka.truststore.path}") truststorePath: String,
    ) = SecurityConfig(
        SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to keystorePath,
        SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to credstorePassword,
        SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to credstorePassword,
        SslConfigs.SSL_KEY_PASSWORD_CONFIG to credstorePassword,
        SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG to "JKS",
        SslConfigs.SSL_KEYSTORE_TYPE_CONFIG to "PKCS12",
        SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to truststorePath,
        CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to "SSL",
    )

    class SecurityConfig(vararg input: Pair<String, Any>) : Map<String, Any> by input.toMap()

    @Bean("producer")
    fun producer(securityConfig: SecurityConfig): KafkaTemplate<String, String> {
        return KafkaTemplate(DefaultKafkaProducerFactory(omsorgsopptjeningProducerConfig() + securityConfig))
    }

    private fun omsorgsopptjeningProducerConfig(): Map<String, Serializable> = mapOf(
        ProducerConfig.CLIENT_ID_CONFIG to "omsorgsopptjening-start-innlesning",
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to aivenBootstrapServers,
    )
}