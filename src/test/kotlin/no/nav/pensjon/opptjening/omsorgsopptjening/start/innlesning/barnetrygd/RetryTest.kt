package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import com.github.tomakehurst.wiremock.stubbing.Scenario
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.barnetrygd.Barnetrygdmelding
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.serialize
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.InnlesningService
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.mockito.BDDMockito.any
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.kafka.core.KafkaTemplate
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals

class RetryTest: SpringContextTest.NoKafka() {
    @Autowired
    lateinit var barnetrygdmottakerRepository: BarnetrygdmottakerRepository

    @Autowired
    lateinit var innlesningService: InnlesningService

    @MockBean
    lateinit var kafkaTemplate: KafkaTemplate<String, String>

    companion object {
        @RegisterExtension
        private val wiremock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
            .build()!!
    }
    @Test
    fun `Gitt en ny status saa skal den kunne retryes 3 ganger for den feiler`() {
        wiremock.stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/api/ekstern/pensjon/hent-barnetrygd"))
                .willReturn(WireMock.serverError())
        )

        barnetrygdmottakerRepository.save(Barnetrygdmottaker("12345678910", 2023, "xx"))
        barnetrygdmottakerRepository.findAll().single().let {
            assertInstanceOf(Status.Klar::class.java, it.status)
        }
        innlesningService.prosesserBarnetrygdmottakere()
        barnetrygdmottakerRepository.findAll().single().let {
            assertInstanceOf(Status.Retry::class.java, it.status).also {
                assertEquals(1, it.antallForsok)
                assertEquals(3, it.maksAntallForsok)
            }
        }
        innlesningService.prosesserBarnetrygdmottakere()
        innlesningService.prosesserBarnetrygdmottakere()
        barnetrygdmottakerRepository.findAll().single().let {
            assertInstanceOf(Status.Feilet::class.java, it.status)
        }
    }

    @Test
    fun `Gitt en ny status saa skal den kunne retryes 1 ganger for den blir ferdig`() {
        given(kafkaTemplate.send(any<ProducerRecord<String,String>>())).willAnswer {
            CompletableFuture.completedFuture(it.arguments[0])
        }

        wiremock.stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/api/ekstern/pensjon/hent-barnetrygd"))
                .inScenario("feilOgFerdig")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(WireMock.serverError())
                .willSetStateTo("ok")
        )

        wiremock.stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/api/ekstern/pensjon/hent-barnetrygd"))
                .inScenario("feilOgFerdig")
                .whenScenarioStateIs("ok")
                .willReturn(WireMock.ok().withBody(serialize(emptyList<Barnetrygdmelding.Sak>())))
        )

        barnetrygdmottakerRepository.save(Barnetrygdmottaker("12345678910", 2023, "xx"))
        barnetrygdmottakerRepository.findAll().single().let {
            assertInstanceOf(Status.Klar::class.java, it.status)
        }
        innlesningService.prosesserBarnetrygdmottakere()
        barnetrygdmottakerRepository.findAll().single().let {
            assertInstanceOf(Status.Retry::class.java, it.status).also {
                assertEquals(1, it.antallForsok)
                assertEquals(3, it.maksAntallForsok)
            }
        }

        innlesningService.prosesserBarnetrygdmottakere()
        barnetrygdmottakerRepository.findAll().single().let {
            assertInstanceOf(Status.Ferdig::class.java, it.status)
        }
    }
}