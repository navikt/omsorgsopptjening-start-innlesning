package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.KafkaMessageType
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Topics
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.barnetrygd.Barnetrygdmelding
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.serialize
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FlowIntegrationTest : SpringContextTest.WithKafka() {

    @Autowired
    lateinit var barnetrygdmottakerRepository: BarnetrygdmottakerRepository

    @Autowired
    lateinit var producedMessageListener: ProducedMessageListener

    companion object {
        @RegisterExtension
        private val wiremock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
            .build()!!
    }

    @Test
    fun test() {
        wiremock.stubFor(
            WireMock.post(urlPathEqualTo("/api/ekstern/pensjon/hent-barnetrygd"))
                .willReturn(WireMock.ok().withBody(serialize(emptyList<Barnetrygdmelding.Sak>())))
        )

        val melding = BarnetrygdmottakerKafkaListener.KafkaMelding("12345678910", 2022)

        sendBarnetrygdMottakerKafka(melding)

        Thread.sleep(1000)

        val barnetrygdmottaker = barnetrygdmottakerRepository.findAll().single()

        wiremock.verify(
            postRequestedFor(urlPathEqualTo("/api/ekstern/pensjon/hent-barnetrygd"))
                .withHeader(CorrelationId.name, equalTo(barnetrygdmottaker.correlationId))
        );

        assertEquals(barnetrygdmottaker.ident, melding.ident)
        assertEquals(barnetrygdmottaker.ar, melding.ar)
        assertNotNull(barnetrygdmottaker.id)
        assertInstanceOf(Status.Ferdig::class.java, barnetrygdmottaker.status)
        assertNotNull(barnetrygdmottaker.correlationId)

        producedMessageListener.removeFirstRecord(5).let {
            assertEquals(
                serialize(
                    Topics.Omsorgsopptjening.Key(
                        ident = "12345678910",
                    )
                ),
                it.key()
            )
            assertEquals(
                serialize(
                    Barnetrygdmelding(
                        ident = "12345678910",
                        list = emptyList()
                    )
                ),
                it.value()
            )
            assertEquals(
                "BARNETRYGD",
                String(it.headers().lastHeader(KafkaMessageType.name).value())
            )
            assertEquals(
                barnetrygdmottaker.correlationId,
                String(it.headers().lastHeader(CorrelationId.name).value())
            )
        }
    }
}