package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.config


import org.apache.kafka.clients.CommonClientConfigs
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.EnableKafka


@EnableKafka
@Configuration
@Profile("kafkaIntegrationTest")
class KafkaIntegrationTestConfig {
    @Bean
    fun securityConfig() = KafkaConfig.SecurityConfig(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to "PLAINTEXT")
}