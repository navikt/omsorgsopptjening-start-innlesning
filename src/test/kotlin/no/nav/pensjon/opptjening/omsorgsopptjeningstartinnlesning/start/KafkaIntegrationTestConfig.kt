package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.start


import org.apache.kafka.clients.CommonClientConfigs
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.EnableKafka


@EnableKafka
@Configuration
@Profile("!no-kafka")
class KafkaIntegrationTestConfig {
    @Bean
    fun securityConfig() = KafkaSecurityConfig(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to "PLAINTEXT")
}