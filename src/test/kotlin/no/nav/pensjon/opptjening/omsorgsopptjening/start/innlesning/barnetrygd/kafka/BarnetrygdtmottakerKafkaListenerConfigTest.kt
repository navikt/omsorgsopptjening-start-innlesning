package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.kafka

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.config.KafkaConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.kafka.listener.ContainerProperties
import java.time.Duration

/**
 * Rein enhetstest av lytter-fabrikken. Låser at Spring Boot 4-migreringa ikkje endra
 * container-oppsettet: authExceptionRetryInterval (4s) blei fjerna i mellomversjonen men
 * metoda finst framleis i spring-kafka 4, så den er restaurert. ackMode er uendra.
 */
class BarnetrygdtmottakerKafkaListenerConfigTest {

    private val config = BarnetrygdtmottakerKafkaListenerConfig(
        aivenBootstrapServers = "localhost:9092",
        customErrorHandler = mock(BarnetrygdmottakerKafkaErrorHandler::class.java),
    )

    @Test
    fun `container beheld manuell ack og 4s auth-retry-intervall`() {
        val props = config.listener(KafkaConfig.SecurityConfig())!!.containerProperties

        assertThat(props.ackMode).isEqualTo(ContainerProperties.AckMode.MANUAL)
        assertThat(props.authExceptionRetryInterval).isEqualTo(Duration.ofSeconds(4))
    }
}
