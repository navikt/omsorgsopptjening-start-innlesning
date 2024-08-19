package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.OutputStreamAppender
import net.logstash.logback.encoder.LogstashEncoder
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.slf4j.LoggerFactory
import org.testcontainers.shaded.org.apache.commons.io.output.ByteArrayOutputStream


/**
 * Tester for å verifisere at alle nødvendige biblioteker knyttet til
 * logging er til stede og kompatible
 */
class LogbackVerificationTest {

    @Test
    fun `logstash-encoder fungerer`() {
        val log = LoggerFactory.getLogger(this::class.java) as ch.qos.logback.classic.Logger
        val os = ByteArrayOutputStream()

        val logstashEncoder = LogstashEncoder()
        logstashEncoder.start()

        val appender = OutputStreamAppender<ILoggingEvent>()
        appender.encoder = logstashEncoder
        appender.context = log.loggerContext
        appender.outputStream = os
        appender.start()
        log.addAppender(appender)

        log.info("hello")
        log.detachAndStopAllAppenders()
        val string = String(os.toByteArray())

        JSONAssert.assertEquals(
            """
                { 
                  "@version" : "1",
                  "message" : "hello",
                  "level" : "INFO"
                }
            """,
            string,
            false)
    }
}