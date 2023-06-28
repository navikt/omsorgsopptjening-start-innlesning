package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.start

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.BarnetrygdMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.BarnetrygdSak
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.serialize
import org.junit.jupiter.api.Assertions.assertTrue
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
            WireMock.post(WireMock.urlPathEqualTo("/api/ekstern/pensjon/hent-barnetrygd"))
                .willReturn(WireMock.ok().withBody(serialize(emptyList<BarnetrygdSak>())))
        )

        val melding = BarnetrygdmottakerKafkaListener.BarnetrygdMottakerMelding("12345678910", 2022)

        sendBarnetrygdMottakerKafka(melding)

        Thread.sleep(1000)

        barnetrygdmottakerRepository.findAll().single().also {
            assertEquals(it.ident, melding.ident)
            assertEquals(it.ar, melding.ar)
            assertNotNull(it.id)
            assertTrue(it.prosessert)
        }

        assertEquals(
            serialize(
                BarnetrygdMelding(
                    ident = "12345678910",
                    list = emptyList()
                )
            ),
            producedMessageListener.removeFirstRecord(5).value()
        )
    }
}