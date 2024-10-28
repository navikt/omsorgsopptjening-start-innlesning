package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.not
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import com.github.tomakehurst.wiremock.stubbing.Scenario
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.deserialize
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Omsorgstype
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.PersongrunnlagMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.SpringContextTest
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.InnlesingRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdmottakerRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.barnetrygd.*
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.hjelpestønad.`hent hjelpestønad ok - har hjelpestønad`
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.hjelpestønad.`hent hjelpestønad ok - ingen hjelpestønad`
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.pdl.`pdl error not_found`
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.pdl.`pdl fnr ett i bruk`
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.pdl.`pdl fnr fra query`
import org.apache.kafka.clients.producer.ProducerRecord
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.mockito.BDDMockito.any
import org.mockito.BDDMockito.given
import org.mockito.kotlin.argumentCaptor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.annotation.DirtiesContext
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.CompletableFuture

@DirtiesContext
class BarnetrygdmottakerServiceTest : SpringContextTest.NoKafka() {
    @Autowired
    private lateinit var barnetrygdmottakerRepository: BarnetrygdmottakerRepository

    @Autowired
    private lateinit var barnetrygdService: BarnetrygdmottakerService

    @Autowired
    private lateinit var sendTilBestemService: SendTilBestemService

    @MockBean
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @MockBean
    private lateinit var clock: Clock

    @Autowired
    private lateinit var innlesingRepository: InnlesingRepository

    companion object {
        @JvmField
        @RegisterExtension
        val wiremock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
            .build()!!
    }

    @Test
    fun `Gitt en ny status saa skal den kunne retryes 3 ganger for den feiler`() {
        wiremock.`pdl fnr fra query`()
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

        barnetrygdService.process()
        barnetrygdmottakerRepository.find(barnetrygdmottaker.id)!!.also {
            assertInstanceOf(Barnetrygdmottaker.Status.Retry::class.java, it.status).also { retry ->
                assertThat(retry.antallForsøk).isEqualTo(1)
                assertThat(retry.maxAntallForsøk).isEqualTo(3)
            }
        }

        barnetrygdService.process()
        barnetrygdService.process()
        barnetrygdService.process()

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

//        wiremock.pdl("12345678910", listOf("12345678910"))
        wiremock.`pdl fnr fra query`()
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
                                        "fagsakEiersIdent":"12345678910",
                                        "barnetrygdPerioder":[
                                            {
                                                "personIdent":"09876543210",
                                                "delingsprosentYtelse":"FULL",
                                                "ytelseTypeEkstern":"ORDINÆR_BARNETRYGD",
                                                "utbetaltPerMnd":2000,
                                                "stønadFom": "2020-01",
                                                "stønadTom": "2025-12",
                                                "sakstypeEkstern":"NASJONAL",
                                                "kildesystem":"BA",
                                                "pensjonstrygdet":null,
                                                "norgeErSekundærlandMedNullUtbetaling":false
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

        barnetrygdService.process()
        barnetrygdmottakerRepository.find(barnetrygdmottaker.id).let {
            assertInstanceOf(Barnetrygdmottaker.Status.Retry::class.java, it!!.status).also { retry ->
                assertThat(retry.antallForsøk).isEqualTo(1)
                assertThat(retry.maxAntallForsøk).isEqualTo(3)
            }
        }

        barnetrygdService.process()
        assertInstanceOf(
            Barnetrygdmottaker.Status.Ferdig::class.java,
            barnetrygdmottakerRepository.find(barnetrygdmottaker.id)!!.status
        )
    }


    @Test
    fun `Dersom barnetrygdmottaker ikke finnes i PDL havner barnetrygdmottaker i retry`() {
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
        wiremock.`pdl error not_found`()

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
                                        "fagsakEiersIdent":"12345678910",
                                        "barnetrygdPerioder":[
                                            {
                                                "personIdent":"09876543210",
                                                "delingsprosentYtelse":"FULL",
                                                "ytelseTypeEkstern":"ORDINÆR_BARNETRYGD",
                                                "utbetaltPerMnd":2000,
                                                "stønadFom": "2020-01",
                                                "stønadTom": "2025-12",
                                                "sakstypeEkstern":"NASJONAL",
                                                "kildesystem":"BA",
                                                "pensjonstrygdet":null,
                                                "norgeErSekundærlandMedNullUtbetaling":false
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

        barnetrygdService.process()
        barnetrygdmottakerRepository.find(barnetrygdmottaker.id).let {
            assertInstanceOf(Barnetrygdmottaker.Status.Retry::class.java, it!!.status).also { retry ->
                assertThat(retry.antallForsøk).isEqualTo(1)
                assertThat(retry.maxAntallForsøk).isEqualTo(3)
            }
        }

        barnetrygdService.process()
        barnetrygdmottakerRepository.find(barnetrygdmottaker.id)!!.let { barnetrygdmottaker ->
            assertThat(barnetrygdmottaker.status)
                .isInstanceOf(Barnetrygdmottaker.Status.Retry::class.java)
        }
    }


    @Test
    fun `oppdaterer status selv om det kastes exception under prosessering`() {
        wiremock.`pdl fnr fra query`()
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

        barnetrygdService.process()

        barnetrygdmottakerRepository.find(barnetrygdmottaker.id).also {
            assertInstanceOf(Barnetrygdmottaker.Status.Retry::class.java, it!!.status).also { retry ->
                assertThat(retry.antallForsøk).isEqualTo(1)
                assertThat(retry.maxAntallForsøk).isEqualTo(3)
            }
        }
    }

    @Test
    fun `oppdaterer status på flere meldinger der det kastes exception for alle`() {
        wiremock.`pdl fnr fra query`()
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

        val barnetrygdmottaker1 = barnetrygdmottakerRepository.insert(
            Barnetrygdmottaker.Transient(
                ident = "12345678910",
                correlationId = CorrelationId.generate(),
                innlesingId = innlesing.id
            )
        )

        val barnetrygdmottaker2 = barnetrygdmottakerRepository.insert(
            Barnetrygdmottaker.Transient(
                ident = "12345678911",
                correlationId = CorrelationId.generate(),
                innlesingId = innlesing.id
            )
        )

        val barnetrygdmottaker3 = barnetrygdmottakerRepository.insert(
            Barnetrygdmottaker.Transient(
                ident = "12345678912",
                correlationId = CorrelationId.generate(),
                innlesingId = innlesing.id
            )
        )

        assertInstanceOf(
            Barnetrygdmottaker.Status.Klar::class.java,
            barnetrygdmottakerRepository.find(barnetrygdmottaker1.id)!!.status
        )

        assertInstanceOf(
            Barnetrygdmottaker.Status.Klar::class.java,
            barnetrygdmottakerRepository.find(barnetrygdmottaker2.id)!!.status
        )

        assertInstanceOf(
            Barnetrygdmottaker.Status.Klar::class.java,
            barnetrygdmottakerRepository.find(barnetrygdmottaker3.id)!!.status
        )


        barnetrygdService.process()

        barnetrygdmottakerRepository.find(barnetrygdmottaker1.id).also {
            assertInstanceOf(Barnetrygdmottaker.Status.Retry::class.java, it!!.status).also { retry ->
                assertThat(retry.antallForsøk).isEqualTo(1)
                assertThat(retry.maxAntallForsøk).isEqualTo(3)
            }
        }
        barnetrygdmottakerRepository.find(barnetrygdmottaker2.id).also {
            assertInstanceOf(Barnetrygdmottaker.Status.Retry::class.java, it!!.status).also { retry ->
                assertThat(retry.antallForsøk).isEqualTo(1)
                assertThat(retry.maxAntallForsøk).isEqualTo(3)
            }
        }
        barnetrygdmottakerRepository.find(barnetrygdmottaker3.id).also {
            assertInstanceOf(Barnetrygdmottaker.Status.Retry::class.java, it!!.status).also { retry ->
                assertThat(retry.antallForsøk).isEqualTo(1)
                assertThat(retry.maxAntallForsøk).isEqualTo(3)
            }
        }
    }

    @Test
    fun `oppdaterer riktig status på flere meldinger der det kastes exception for en`() {

        val captor = argumentCaptor<ProducerRecord<String, String>> { }
        given(kafkaTemplate.send(captor.capture())).willAnswer {
            CompletableFuture.completedFuture(it.arguments[0])
        }

        wiremock.`pdl fnr fra query`()
        wiremock.stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/api/ekstern/pensjon/hent-barnetrygd"))
                .withRequestBody(equalToJson("""{"ident":"12345678911","fraDato":"2023-01-01"}"""))
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
        wiremock.stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/api/ekstern/pensjon/hent-barnetrygd"))
                .withRequestBody(not(equalToJson("""{"ident":"12345678911","fraDato":"2023-01-01"}""")))
                .willReturn(
                    WireMock.ok()
                        .withTransformers("response-template")
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBodyFile("barnetrygd/fagsak_for_fnr_fra_query.json")
                )
        )

        wiremock.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/api/hjelpestonad"))
                .willReturn(
                    WireMock.ok()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""[]""")
                )
        )

        /**
         * Stiller klokka litt fram i tid for å unngå at [Barnetrygdmottaker.Status.Retry.karanteneTil] fører til at vi hopper over raden.
         */
        given(clock.instant()).willReturn(Instant.now().plus(10, ChronoUnit.DAYS))

        val innlesing = lagreFullførtInnlesing()

        val barnetrygdmottaker1 = barnetrygdmottakerRepository.insert(
            Barnetrygdmottaker.Transient(
                ident = "12345678910",
                correlationId = CorrelationId.generate(),
                innlesingId = innlesing.id
            )
        )

        val barnetrygdmottaker2 = barnetrygdmottakerRepository.insert(
            Barnetrygdmottaker.Transient(
                ident = "12345678911",
                correlationId = CorrelationId.generate(),
                innlesingId = innlesing.id
            )
        )

        val barnetrygdmottaker3 = barnetrygdmottakerRepository.insert(
            Barnetrygdmottaker.Transient(
                ident = "12345678912",
                correlationId = CorrelationId.generate(),
                innlesingId = innlesing.id
            )
        )

        assertInstanceOf(
            Barnetrygdmottaker.Status.Klar::class.java,
            barnetrygdmottakerRepository.find(barnetrygdmottaker1.id)!!.status
        )

        assertInstanceOf(
            Barnetrygdmottaker.Status.Klar::class.java,
            barnetrygdmottakerRepository.find(barnetrygdmottaker2.id)!!.status
        )

        assertInstanceOf(
            Barnetrygdmottaker.Status.Klar::class.java,
            barnetrygdmottakerRepository.find(barnetrygdmottaker3.id)!!.status
        )


        barnetrygdService.process()

        wiremock.allServeEvents.forEach {
            println("REQUEST: ")
            println(it.request.bodyAsString)
            println("RESPONSE: ")
            println(it.response.bodyAsString)
        }

        barnetrygdmottakerRepository.find(barnetrygdmottaker1.id).also {
            assertInstanceOf(Barnetrygdmottaker.Status.Ferdig::class.java, it!!.status)
        }
        barnetrygdmottakerRepository.find(barnetrygdmottaker2.id).also {
            assertInstanceOf(Barnetrygdmottaker.Status.Retry::class.java, it!!.status).also { retry ->
                assertThat(retry.antallForsøk).isEqualTo(1)
                assertThat(retry.maxAntallForsøk).isEqualTo(3)
            }
        }
        barnetrygdmottakerRepository.find(barnetrygdmottaker3.id).also {
            assertThat(it?.status).isInstanceOf(Barnetrygdmottaker.Status.Ferdig::class.java)
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

        wiremock.`pdl fnr ett i bruk`()
        wiremock.`hent-barnetrygd ok`()
        wiremock.`hent hjelpestønad ok - har hjelpestønad`()

        barnetrygdService.process()
        sendTilBestemService.process()

        deserialize<PersongrunnlagMelding>(captor.allValues.single().value()).also { persongrunnlagMelding ->
            persongrunnlagMelding.persongrunnlag.single().also { sak ->
                assertThat(sak.omsorgsperioder.count { it.omsorgstype == Omsorgstype.FULL_BARNETRYGD }).isEqualTo(1)
                assertThat(
                    sak.hjelpestønadsperioder.count {
                        it.omsorgstype == Omsorgstype.HJELPESTØNAD_FORHØYET_SATS_3
                    }
                ).isEqualTo(1)
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

        wiremock.`pdl fnr fra query`()
        wiremock.`hent-barnetrygd ok`()
        wiremock.`hent hjelpestønad ok - ingen hjelpestønad`()

        barnetrygdService.process()
        sendTilBestemService.process()

        deserialize<PersongrunnlagMelding>(captor.allValues.single().value()).also { persongrunnlagMelding ->
            persongrunnlagMelding.persongrunnlag.single().also { sak ->
                assertThat(sak.omsorgsperioder).hasSize(1)
                assertThat(sak.omsorgsperioder.count { it.omsorgstype == Omsorgstype.FULL_BARNETRYGD }).isEqualTo(1)
                assertThat(sak.hjelpestønadsperioder).isEmpty()
            }
        }
    }

    @Test
    fun `omsorgsgrunnlag kan sende både tom barnetrygd og hjelpestønad dersom ingen eksisterer`() {
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

        wiremock.`pdl fnr ett i bruk`()
        wiremock.`hent-barnetrygd ok - ingen perioder`()
        wiremock.`hent hjelpestønad ok - ingen hjelpestønad`()

        barnetrygdService.process()
        sendTilBestemService.process()

        deserialize<PersongrunnlagMelding>(captor.allValues.single().value()).also { persongrunnlagMelding ->
            persongrunnlagMelding.persongrunnlag.single().also { sak ->
                assertThat(sak.omsorgsperioder).isEmpty()
                assertThat(sak.hjelpestønadsperioder).isEmpty()
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
        val startet = innlesingRepository.start(bestilt.startet(1))
        return innlesingRepository.fullført(startet.ferdig())
    }
}