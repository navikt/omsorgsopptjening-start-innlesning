package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import com.github.tomakehurst.wiremock.stubbing.Scenario
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.serialize
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Innlesing
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.InnlesingRepo
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import org.mockito.BDDMockito.any
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.kafka.core.KafkaTemplate
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals

class RetryTest : SpringContextTest.NoKafka() {
    @Autowired
    private lateinit var barnetrygdmottakerRepository: BarnetrygdmottakerRepository

    @Autowired
    private lateinit var barnetrygdService: BarnetrygdService

    @MockBean
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @MockBean
    private lateinit var clock: Clock

    @Autowired
    private lateinit var innlesingRepo: InnlesingRepo

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
                .willReturn(WireMock.badRequest())
        )
        /**
         * Stiller klokka litt fram i tid for å unngå at [Barnetrygdmottaker.Status.Retry.karanteneTil] fører til at vi hopper over raden.
         */
        given(clock.instant()).willReturn(Instant.now().plus(10, ChronoUnit.DAYS))

        val innlesing = innlesingRepo.bestilt(Innlesing(id = InnlesingId.generate(), år = 2023))
            .also { innlesingRepo.fullført(id = it.id.toString()) }

        val barnetrygdmottaker = barnetrygdmottakerRepository.save(
            Barnetrygdmottaker(
                ident = "12345678910",
                correlationId = CorrelationId.generate(),
                innlesingId = innlesing.id
            )
        )
        assertInstanceOf(
            Barnetrygdmottaker.Status.Klar::class.java,
            barnetrygdmottakerRepository.find(barnetrygdmottaker.id!!).status
        )

        barnetrygdService.process()
        barnetrygdmottakerRepository.find(barnetrygdmottaker.id!!).also {
            assertInstanceOf(Barnetrygdmottaker.Status.Retry::class.java, it.status).also {
                assertEquals(1, it.antallForsøk)
                assertEquals(3, it.maxAntallForsøk)
            }
        }
        barnetrygdService.process()
        barnetrygdService.process()
        barnetrygdService.process()

        assertInstanceOf(
            Barnetrygdmottaker.Status.Feilet::class.java,
            barnetrygdmottakerRepository.find(barnetrygdmottaker.id!!).status
        )
    }

    @Test
    fun `Gitt en ny status saa skal den kunne retryes 1 ganger for den blir ferdig`() {
        given(kafkaTemplate.send(any<ProducerRecord<String, String>>())).willAnswer {
            CompletableFuture.completedFuture(it.arguments[0])
        }
        /**
         * Stiller klokka litt fram i tid for å unngå at [Barnetrygdmottaker.Status.Retry.karanteneTil] fører til at vi hopper over raden.
         */
        given(clock.instant()).willReturn(Instant.now().plus(10, ChronoUnit.DAYS))

        val innlesing = innlesingRepo.bestilt(Innlesing(id = InnlesingId.generate(), år = 2023))
            .also { innlesingRepo.fullført(id = it.id.toString()) }

        wiremock.stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/api/ekstern/pensjon/hent-barnetrygd"))
                .inScenario("feilOgFerdig")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(WireMock.forbidden())
                .willSetStateTo("ok")
        )

        wiremock.stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/api/ekstern/pensjon/hent-barnetrygd"))
                .inScenario("feilOgFerdig")
                .whenScenarioStateIs("ok")
                .willReturn(
                    WireMock.ok()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(
                            serialize(
                                listOf(
                                    Barnetrygdmelding.Sak(
                                        fagsakId = "1",
                                        fagsakEiersIdent = "12345678910",
                                        barnetrygdPerioder = emptyList()
                                    )
                                )
                            )
                        )
                )
        )

        val barnetrygdmottaker = barnetrygdmottakerRepository.save(
            Barnetrygdmottaker(
                ident = "12345678910",
                correlationId = CorrelationId.generate(),
                innlesingId = innlesing.id
            )
        )

        assertInstanceOf(
            Barnetrygdmottaker.Status.Klar::class.java,
            barnetrygdmottakerRepository.find(barnetrygdmottaker.id!!).status
        )

        barnetrygdService.process()
        barnetrygdmottakerRepository.find(barnetrygdmottaker.id!!).let {
            assertInstanceOf(Barnetrygdmottaker.Status.Retry::class.java, it.status).also {
                assertEquals(1, it.antallForsøk)
                assertEquals(3, it.maxAntallForsøk)
            }
        }

        barnetrygdService.process()
        assertInstanceOf(
            Barnetrygdmottaker.Status.Ferdig::class.java,
            barnetrygdmottakerRepository.find(barnetrygdmottaker.id!!).status
        )
    }

    @Test
    fun `oppdaterer status selv om det kastes exception under prosessering`() {
        wiremock.stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/api/ekstern/pensjon/hent-barnetrygd"))
                .willReturn(
                    WireMock.ok()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(
                            """
                                {
                                    "dette er ikke json"
                                }
                            """.trimIndent()
                        )
                )
        )
        /**
         * Stiller klokka litt fram i tid for å unngå at [Barnetrygdmottaker.Status.Retry.karanteneTil] fører til at vi hopper over raden.
         */
        given(clock.instant()).willReturn(Instant.now().plus(10, ChronoUnit.DAYS))

        val innlesing = innlesingRepo.bestilt(Innlesing(id = InnlesingId.generate(), år = 2023))
            .also { innlesingRepo.fullført(id = it.id.toString()) }

        val barnetrygdmottaker = barnetrygdmottakerRepository.save(
            Barnetrygdmottaker(
                ident = "12345678910",
                correlationId = CorrelationId.generate(),
                innlesingId = innlesing.id
            )
        )

        assertInstanceOf(
            Barnetrygdmottaker.Status.Klar::class.java,
            barnetrygdmottakerRepository.find(barnetrygdmottaker.id!!).status
        )

        assertThrows<MismatchedInputException> {
            barnetrygdService.process()
        }

        barnetrygdmottakerRepository.find(barnetrygdmottaker.id!!).also {
            assertInstanceOf(Barnetrygdmottaker.Status.Retry::class.java, it.status).also {
                assertEquals(1, it.antallForsøk)
                assertEquals(3, it.maxAntallForsøk)
            }
        }
    }
}