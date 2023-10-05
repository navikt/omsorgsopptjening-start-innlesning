package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import com.github.tomakehurst.wiremock.stubbing.Scenario
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.deserialize
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Omsorgstype
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.PersongrunnlagMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.SpringContextTest
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.HentBarnetrygdException
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.`hent hjelpestønad ok - har hjelpestønad`
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.`hent hjelpestønad ok - ingen hjelpestønad`
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.`hent-barnetrygd ok`
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdInnlesingRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdmottakerRepository
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import org.mockito.BDDMockito.any
import org.mockito.BDDMockito.given
import org.mockito.kotlin.argumentCaptor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.kafka.core.KafkaTemplate
import java.lang.reflect.UndeclaredThrowableException
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals

class BarnetrygdmottakerServiceTest : SpringContextTest.NoKafka() {
    @Autowired
    private lateinit var barnetrygdmottakerRepository: BarnetrygdmottakerRepository

    @Autowired
    private lateinit var barnetrygdService: BarnetrygdmottakerService

    @MockBean
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @MockBean
    private lateinit var clock: Clock

    @Autowired
    private lateinit var innlesingRepository: BarnetrygdInnlesingRepository

    companion object {
        @JvmField
        @RegisterExtension
        val wiremock = WireMockExtension.newInstance()
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

        val innlesing = lagreFullførtInnlesing()

        val barnetrygdmottaker = barnetrygdmottakerRepository.insert(
            Barnetrygdmottaker.Transient(
                ident = "12345678910",
                correlationId = CorrelationId.generate(),
                innlesingId = innlesing.id
            )
        )
        assertInstanceOf(
            Barnetrygdmottaker.Status.Klar::class.java,
            barnetrygdmottakerRepository.find(barnetrygdmottaker.id)!!.status
        )

        assertThrows<HentBarnetrygdException> {
            barnetrygdService.process()
        }
        barnetrygdmottakerRepository.find(barnetrygdmottaker.id)!!.also {
            assertInstanceOf(Barnetrygdmottaker.Status.Retry::class.java, it.status).also {
                assertEquals(1, it.antallForsøk)
                assertEquals(3, it.maxAntallForsøk)
            }
        }

        assertThrows<HentBarnetrygdException> {
            barnetrygdService.process()
        }
        assertThrows<HentBarnetrygdException> {
            barnetrygdService.process()
        }
        assertThrows<HentBarnetrygdException> {
            barnetrygdService.process()
        }

        assertInstanceOf(
            Barnetrygdmottaker.Status.Feilet::class.java,
            barnetrygdmottakerRepository.find(barnetrygdmottaker.id)!!.status
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

        val innlesing = lagreFullførtInnlesing()

        val barnetrygdmottaker = barnetrygdmottakerRepository.insert(
            Barnetrygdmottaker.Transient(
                ident = "12345678910",
                correlationId = CorrelationId.generate(),
                innlesingId = innlesing.id
            )
        )

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
                            """
                            {
                                "fagsaker": [
                                    {
                                        "fagsakId":"1",
                                        "fagsakEiersIdent":"12345678910",
                                        "barnetrygdPerioder":[
                                            {
                                                "personIdent":"09876543210",
                                                "delingsprosentYtelse":"FULL",
                                                "ytelseTypeEkstern":"ORDINÆR_BARNETRYGD",
                                                "utbetaltPerMnd":2000,
                                                "stønadFom": "2020-01",
                                                "stønadTom": "2025-12",
                                                "kilde":"BA"                                                                                                                                                
                                            }                                                                                          
                                        ]
                                    }
                                ]
                            }
                            """.trimIndent()
                        )
                )
        )

        wiremock.`hent hjelpestønad ok - ingen hjelpestønad`()

        assertInstanceOf(
            Barnetrygdmottaker.Status.Klar::class.java,
            barnetrygdmottakerRepository.find(barnetrygdmottaker.id)!!.status
        )

        assertThrows<HentBarnetrygdException> {
            barnetrygdService.process()
        }
        barnetrygdmottakerRepository.find(barnetrygdmottaker.id).let {
            assertInstanceOf(Barnetrygdmottaker.Status.Retry::class.java, it!!.status).also {
                assertEquals(1, it.antallForsøk)
                assertEquals(3, it.maxAntallForsøk)
            }
        }

        barnetrygdService.process()
        assertInstanceOf(
            Barnetrygdmottaker.Status.Ferdig::class.java,
            barnetrygdmottakerRepository.find(barnetrygdmottaker.id)!!.status
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

        val innlesing = lagreFullførtInnlesing()

        val barnetrygdmottaker = barnetrygdmottakerRepository.insert(
            Barnetrygdmottaker.Transient(
                ident = "12345678910",
                correlationId = CorrelationId.generate(),
                innlesingId = innlesing.id
            )
        )

        assertInstanceOf(
            Barnetrygdmottaker.Status.Klar::class.java,
            barnetrygdmottakerRepository.find(barnetrygdmottaker.id)!!.status
        )

        assertThrows<UndeclaredThrowableException> {
            barnetrygdService.process()
        }

        barnetrygdmottakerRepository.find(barnetrygdmottaker.id).also {
            assertInstanceOf(Barnetrygdmottaker.Status.Retry::class.java, it!!.status).also {
                assertEquals(1, it.antallForsøk)
                assertEquals(3, it.maxAntallForsøk)
            }
        }
    }

    @Test
    fun `omsorgsgrunnlag berikes med hjelpestønad dersom det eksisterer`() {
        val captor = argumentCaptor<ProducerRecord<String, String>> { }
        given(kafkaTemplate.send(captor.capture())).willAnswer {
            CompletableFuture.completedFuture(it.arguments[0])
        }
        given(clock.instant()).willReturn(Instant.now())

        val innlesing = lagreFullførtInnlesing()

        barnetrygdmottakerRepository.insert(
            Barnetrygdmottaker.Transient(
                ident = "12345678910",
                correlationId = CorrelationId.generate(),
                innlesingId = innlesing.id
            )
        )

        wiremock.`hent-barnetrygd ok`()
        wiremock.`hent hjelpestønad ok - har hjelpestønad`()

        barnetrygdService.process()

        deserialize<PersongrunnlagMelding>(captor.allValues.single().value()).also { PersongrunnlagMelding ->
            PersongrunnlagMelding.persongrunnlag.single().also { sak ->
                assertEquals(2, sak.omsorgsperioder.count())
                assertEquals(1, sak.omsorgsperioder.count { it.omsorgstype == Omsorgstype.FULL_BARNETRYGD })
                assertEquals(
                    1,
                    sak.omsorgsperioder.count { it.omsorgstype == Omsorgstype.HJELPESTØNAD_FORHØYET_SATS_3 })
            }
        }
    }

    @Test
    fun `omsorgsgrunnlag sender bare barnetrygd dersom hjelpestønad ikke eksisterer`() {
        val captor = argumentCaptor<ProducerRecord<String, String>> { }
        given(kafkaTemplate.send(captor.capture())).willAnswer {
            CompletableFuture.completedFuture(it.arguments[0])
        }
        given(clock.instant()).willReturn(Instant.now())

        val innlesing = lagreFullførtInnlesing()

        barnetrygdmottakerRepository.insert(
            Barnetrygdmottaker.Transient(
                ident = "12345678910",
                correlationId = CorrelationId.generate(),
                innlesingId = innlesing.id
            )
        )

        wiremock.`hent-barnetrygd ok`()
        wiremock.`hent hjelpestønad ok - ingen hjelpestønad`()

        barnetrygdService.process()

        deserialize<PersongrunnlagMelding>(captor.allValues.single().value()).also { PersongrunnlagMelding ->
            PersongrunnlagMelding.persongrunnlag.single().also { sak ->
                assertEquals(1, sak.omsorgsperioder.count())
                assertEquals(1, sak.omsorgsperioder.count { it.omsorgstype == Omsorgstype.FULL_BARNETRYGD })
            }
        }
    }

    private fun lagreFullførtInnlesing(): BarnetrygdInnlesing {
        val bestilt = innlesingRepository.bestilt(
            BarnetrygdInnlesing.Bestilt(
                id = InnlesingId.generate(),
                år = 2023,
                forespurtTidspunkt = Instant.now()
            )
        )
        val startet = innlesingRepository.start(bestilt.startet())
        return innlesingRepository.fullført(startet.ferdig())
    }
}