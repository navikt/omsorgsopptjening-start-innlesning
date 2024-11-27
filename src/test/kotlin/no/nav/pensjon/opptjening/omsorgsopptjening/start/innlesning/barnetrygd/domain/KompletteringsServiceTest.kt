package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Rådata
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.RådataFraKilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.*
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Mdc
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.SpringContextTest
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.barnetrygd.WiremockFagsak
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.barnetrygd.`hent-barnetrygd ok - ingen perioder`
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.barnetrygd.`hent-barnetrygd ok uten fagsaker`
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.barnetrygd.`hent-barnetrygd-med-fagsaker`
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.hjelpestønad.`hent hjelpestønad ok - har hjelpestønad`
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.hjelpestønad.`hent hjelpestønad ok - ingen hjelpestønad`
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.pdl.pdl
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.pdl.`pdl - ingen gjeldende`
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.pdl.`pdl error not_found`
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.pdl.`pdl fnr fra query`
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import java.lang.String.format
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.*
import java.util.function.Consumer


class KompletteringsServiceTest : SpringContextTest.NoKafka() {

    @Autowired
    private lateinit var kompletteringsService: KompletteringsService

    companion object {
        @JvmField
        @RegisterExtension
        val wiremock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
            .build()!!
    }

    @Test
    fun `Komprimerer barnetrygdData med én barnetrygdsak`() {
        val sak = persongrunnlag("12345678901", "12345678920", 2018)
        val response = HentBarnetrygdResponse(
            barnetrygdsaker = listOf(
                sak
            ),
            rådataFraKilde = RådataFraKilde(emptyMap())
        )

        val barnetrygdData = KompletteringsService.PersongrunnlagOgRådata(
            persongrunnlag = response.barnetrygdsaker,
            rådataFraKilde = listOf(response.rådataFraKilde),
        )
        val saker = barnetrygdData.komprimer()
        assertThat(saker.persongrunnlag).containsOnly(sak)
    }

    @Test
    fun `Komprimerer barnetrygdData med flere barnetrygdsaker for ulike omsorgsytere med ulike perioder`() {
        val sak1 = persongrunnlag("12345678901", "12345678920", 2018)
        val sak2 = persongrunnlag("12345678902", "12345678920", 2019)
        val response = HentBarnetrygdResponse(
            barnetrygdsaker = listOf(
                sak1, sak2
            ),
            rådataFraKilde = RådataFraKilde(emptyMap())
        )

        val barnetrygdData = KompletteringsService.PersongrunnlagOgRådata(
            persongrunnlag = response.barnetrygdsaker,
            rådataFraKilde = listOf(response.rådataFraKilde),
        )
        val saker = barnetrygdData.komprimer()
        assertThat(saker.persongrunnlag).containsExactly(sak1, sak2)
    }

    @Test
    fun `Komprimerer barnetrygdData med flere forekomster av samme barnetrygdsak`() {
        val sak = persongrunnlag("12345678901", "12345678920", 2018)
        val response = HentBarnetrygdResponse(
            barnetrygdsaker = listOf(
                sak, sak,
            ),
            rådataFraKilde = RådataFraKilde(emptyMap())
        )

        val barnetrygdData = KompletteringsService.PersongrunnlagOgRådata(
            persongrunnlag = response.barnetrygdsaker,
            rådataFraKilde = listOf(response.rådataFraKilde),
        )
        val saker = barnetrygdData.komprimer()
        assertThat(saker.persongrunnlag).containsExactly(sak)
    }

    @Test
    fun `oppdater BarnetrygdData, alle fnr er gjeldende`() {
        wiremock.`pdl fnr fra query`()

        val sak1 = persongrunnlag("12345678901", "12345678920", 2018)
        val sak2 = persongrunnlag("12345678902", "12345678921", 2019)
        val sak3 = persongrunnlag("12345678903", "12345678922", 2019)
        val response1 = HentBarnetrygdResponse(
            barnetrygdsaker = listOf(
                sak1, sak2
            ),
            rådataFraKilde = RådataFraKilde(emptyMap())
        )
        val response2 = HentBarnetrygdResponse(
            barnetrygdsaker = listOf(
                sak3
            ),
            rådataFraKilde = RådataFraKilde(emptyMap())
        )

        val barnetrygdData = KompletteringsService.PersongrunnlagOgRådata(
            persongrunnlag = listOf(response1, response2).flatMap { it.barnetrygdsaker },
            rådataFraKilde = listOf(response1, response2).map { it.rådataFraKilde }
        )

        val oppdatertBarnetrygdData =
            Mdc.scopedMdc(CorrelationId.generate()) {
                Mdc.scopedMdc(InnlesingId.generate()) {
                    kompletteringsService.oppdaterAlleFnr(barnetrygdData)
                }
            }
        assertThat(oppdatertBarnetrygdData.copy(rådataFraKilde = Rådata(emptyList())))
            .isEqualTo(barnetrygdData.copy(rådataFraKilde = Rådata(emptyList())))
    }

    @Test
    fun `full flyt med fnr-historikk og oppdatering`() {
        fun fnr(i: Int) = Ident(format("%011d", i))
        wiremock.pdl(fnr(1), listOf(fnr(1_1), fnr(1_2), fnr(1_3)))
        wiremock.pdl(fnr(2), listOf(fnr(2_1), fnr(2_2), fnr(2_3)))
        wiremock.pdl(fnr(3), listOf(fnr(3_1), fnr(3_2), fnr(3_3)))

        val fnrUtenBarnetrygdSaker =
            setOf(
                fnr(1), fnr(1_1),
                fnr(2), fnr(2_1), fnr(2_2), fnr(2_3),
                fnr(3), fnr(3_1), fnr(3_2), fnr(3_3),
            )
        fnrUtenBarnetrygdSaker.forEach {
            wiremock.`hent-barnetrygd ok uten fagsaker`(it)
        }

        wiremock.`hent-barnetrygd-med-fagsaker`(
            forFnr = fnr(1_2),
            fagsaker = listOf(
                WiremockFagsak(
                    eier = fnr(1_1), perioder =
                    listOf(
                        WiremockFagsak.BarnetrygdPeriode(personIdent = fnr(2_1)),
                        WiremockFagsak.BarnetrygdPeriode(personIdent = fnr(2_2)),
                    )
                ),
                WiremockFagsak(
                    eier = fnr(1_2), perioder =
                    listOf(
                        WiremockFagsak.BarnetrygdPeriode(personIdent = fnr(2_3)),
                    )
                ),
            )
        )
        wiremock.`hent-barnetrygd-med-fagsaker`(
            forFnr = fnr(1_3),
            fagsaker = listOf(
                WiremockFagsak(
                    eier = fnr(1_3), perioder =
                    listOf(
                        WiremockFagsak.BarnetrygdPeriode(personIdent = fnr(2_1)),
                        WiremockFagsak.BarnetrygdPeriode(personIdent = fnr(3_2)),
                    )
                ),
                WiremockFagsak(
                    eier = fnr(1_2), perioder =
                    listOf(
                        WiremockFagsak.BarnetrygdPeriode(personIdent = fnr(3_3)),
                    )
                ),
            )
        )

        val fnrUtenHjelpestønad = setOf(
            fnr(1), fnr(1_1), fnr(1_2), fnr(1_3),
            fnr(2), fnr(2_1), fnr(2_2), fnr(2_3),
            fnr(3), fnr(3_2),
        )
        fnrUtenHjelpestønad.forEach {
            wiremock.`hent hjelpestønad ok - ingen hjelpestønad`(it)
        }
        wiremock.`hent hjelpestønad ok - har hjelpestønad`(fnr(3_1))
        wiremock.`hent hjelpestønad ok - har hjelpestønad`(fnr(3_3))

        val mottatt = Barnetrygdmottaker.Mottatt(
            id = UUID.randomUUID(),
            opprettet = Instant.now(),
            ident = fnr(1_2),
            personId = null,
            correlationId = CorrelationId.generate(),
            innlesingId = InnlesingId.generate(),
            statushistorikk = emptyList(),
            år = 2022,
        )

        val komplettert =
            Mdc.scopedMdc(mottatt.correlationId) {
                Mdc.scopedMdc(mottatt.innlesingId) {
                    kompletteringsService.kompletter(
                        mottatt
                    )
                }
            }
        assertThat(komplettert.barnetrygdmottaker.personId?.fnr).isEqualTo(fnr(1))
        assertThat(komplettert.persongrunnlag).hasSize(1)
        assertThat(komplettert.persongrunnlag[0].omsorgsyter).isEqualTo(fnr(1).value)
        assertThat(komplettert.persongrunnlag[0].omsorgsperioder).hasSize(2)
        assertThat(komplettert.persongrunnlag[0].hjelpestønadsperioder).hasSize(1)
        assertThat(komplettert.rådata).hasSize(15)

        assertThat(
            komplettert.rådata.filter {
                it.containsKey("barnetrygd")
            }
        ).satisfies(Consumer<List<RådataFraKilde>> {
            assertThat(it.map { it["fnr"] }).containsExactlyInAnyOrder(
                fnr(1).value,
                fnr(1_1).value,
                fnr(1_2).value,
                fnr(1_3).value,
            )
        })

        assertThat(
            komplettert.rådata.filter {
                it.containsKey("hjelpestønad")
            }
        ).satisfies(Consumer<List<RådataFraKilde>> {
            assertThat(it.map { it["fnr"] }).containsExactlyInAnyOrder(
                fnr(2).value,
                fnr(2_1).value,
                fnr(2_2).value,
                fnr(2_3).value,
                fnr(3).value,
                fnr(3_1).value,
                fnr(3_2).value,
                fnr(3_3).value,
            )
        })

        assertThat(
            komplettert.rådata.filter {
                !it.containsKey("barnetrygd") && !it.containsKey("hjelpestønad")
            }
        ).satisfies(Consumer<List<RådataFraKilde>> {
            assertThat(it.flatMap { it.values.toList() })
                .hasSize(3)
                .satisfiesExactlyInAnyOrder(
                    Consumer {
                        assertThat(it).contains(""""identifikasjonsnummer": "00000000001"""")
                    },
                    Consumer {
                        assertThat(it).contains(""""identifikasjonsnummer": "00000000002"""")
                    },
                    Consumer {
                        assertThat(it).contains(""""identifikasjonsnummer": "00000000003"""")
                    }
                )
        })
    }


    @Test
    fun `barnetrygdmottaker finnes ikke i PDL`() {
        fun fnr(i: Int) = Ident(format("%011d", i))
        wiremock.`pdl error not_found`()

        wiremock.`hent-barnetrygd ok - ingen perioder`()

        val mottatt = Barnetrygdmottaker.Mottatt(
            id = UUID.randomUUID(),
            opprettet = Instant.now(),
            ident = fnr(1_1),
            personId = null,
            correlationId = CorrelationId.generate(),
            innlesingId = InnlesingId.generate(),
            statushistorikk = emptyList(),
            år = 2022,
        )

        val komplettert =
            Mdc.scopedMdc(mottatt.correlationId) {
                Mdc.scopedMdc(mottatt.innlesingId) {
                    kompletteringsService.kompletter(
                        mottatt
                    )
                }
            }
        assertThat(komplettert.feilinformasjon)
            .hasSize(1)
            .first()
            .isInstanceOf(Feilinformasjon.UgyldigIdent::class.java)
            .hasFieldOrPropertyWithValue("ident", fnr(1_1).value)
            .hasFieldOrPropertyWithValue("identRolle", IdentRolle.BARNETRYGDMOTTAKER)
        assertThat(komplettert.rådata.isEmpty())
    }

    @Test
    fun `omsorgsmottaker finnes ikke i PDL`() {
        fun fnr(i: Int) = Ident(format("%011d", i))
        wiremock.pdl(fnr(1), emptyList())
        wiremock.`pdl error not_found`(fnr(2).value)

        wiremock.`hent-barnetrygd-med-fagsaker`(
            forFnr = fnr(1),
            fagsaker = listOf(
                WiremockFagsak(
                    eier = fnr(1),
                    perioder = listOf(
                        WiremockFagsak.BarnetrygdPeriode(
                            personIdent = fnr(2),
                        )
                    )
                )
            )
        )

        val mottatt = Barnetrygdmottaker.Mottatt(
            id = UUID.randomUUID(),
            opprettet = Instant.now(),
            ident = fnr(1),
            personId = null,
            correlationId = CorrelationId.generate(),
            innlesingId = InnlesingId.generate(),
            statushistorikk = emptyList(),
            år = 2022,
        )

        val komplettert =
            Mdc.scopedMdc(mottatt.correlationId) {
                Mdc.scopedMdc(mottatt.innlesingId) {
                    kompletteringsService.kompletter(
                        mottatt
                    )
                }
            }
        assertThat(komplettert.feilinformasjon)
            .hasSize(1)
            .first()
            .isInstanceOf(Feilinformasjon.UgyldigIdent::class.java)
            .hasFieldOrPropertyWithValue("ident", fnr(2).value)
            .hasFieldOrPropertyWithValue("identRolle", IdentRolle.OMSORGSMOTTAKER_BARNETRYGD)
        println("SIZE: " + komplettert.rådata.size)
        println("RÅDATA: ${komplettert.rådata}")
        assertThat(komplettert.rådata)
            .filteredOn { it.containsKey("barnetrygd") }
            .hasSize(1)
        assertThat(komplettert.rådata)
            .filteredOn { !it.containsKey("barnetrygd") }
            .satisfiesExactlyInAnyOrder(
                Consumer {
                    assertThat(it).containsKey(fnr(1).value)
                },
                Consumer {
                    assertThat(it).containsKey(fnr(2).value)
                }
            )
    }

    @Test
    fun `omsorgsmottaker har overlappende perioder`() {
        fun fnr(i: Int) = Ident(format("%011d", i))
        wiremock.pdl(fnr(1), listOf(fnr(1_1), fnr(1_2)))
        wiremock.pdl(fnr(2), listOf(fnr(2_1), fnr(2_2)))

        val fnrUtenBarnetrygdSaker =
            setOf(
                fnr(1),
            )
        fnrUtenBarnetrygdSaker.forEach {
            wiremock.`hent-barnetrygd ok uten fagsaker`(it)
        }

        wiremock.`hent-barnetrygd-med-fagsaker`(
            forFnr = fnr(1_1),
            fagsaker = listOf(
                WiremockFagsak(
                    eier = fnr(1_1), perioder =
                    listOf(
                        WiremockFagsak.BarnetrygdPeriode(personIdent = fnr(2_1)),
                        WiremockFagsak.BarnetrygdPeriode(personIdent = fnr(2_2)),
                    )
                ),
                WiremockFagsak(
                    eier = fnr(1_2), perioder =
                    listOf(
                        WiremockFagsak.BarnetrygdPeriode(personIdent = fnr(2_1)),
                    )
                ),
            )
        )
        wiremock.`hent-barnetrygd-med-fagsaker`(
            forFnr = fnr(1_2),
            fagsaker = listOf(
                WiremockFagsak(
                    eier = fnr(1_2), perioder =
                    listOf(
                        WiremockFagsak.BarnetrygdPeriode(
                            personIdent = fnr(2_1),
                            stønadFom = YearMonth.of(2022, 3),
                            stønadTom = YearMonth.of(2023, 10)
                        ),
                    )
                ),
            )
        )

        wiremock.`hent hjelpestønad ok - ingen hjelpestønad`()

        val mottatt = Barnetrygdmottaker.Mottatt(
            id = UUID.randomUUID(),
            opprettet = Instant.now(),
            ident = fnr(1_2),
            personId = null,
            correlationId = CorrelationId.generate(),
            innlesingId = InnlesingId.generate(),
            statushistorikk = emptyList(),
            år = 2022,
        )


        val komplettert =
            Mdc.scopedMdc(mottatt.correlationId) {
                Mdc.scopedMdc(mottatt.innlesingId) {
                    kompletteringsService.kompletter(
                        mottatt
                    )
                }
            }
        assertThat(komplettert.feilinformasjon)
            .hasSize(1)
            .first()
            .isInstanceOf(Feilinformasjon.OverlappendeBarnetrygdperioder::class.java)
        assertThat(komplettert.rådata)
            .filteredOn { it.containsKey("barnetrygd") }
            .hasSize(3)

        assertThat(komplettert.rådata)
            .filteredOn { !it.containsKey("barnetrygd") }
            .hasSize(2)
    }

    @Test
    fun `omsorgsmottaker har overlappende perioder fra samme kall`() {
        fun fnr(i: Int) = Ident(format("%011d", i))
        wiremock.`pdl fnr fra query`()

        wiremock.`hent-barnetrygd-med-fagsaker`(
            forFnr = fnr(1),
            fagsaker = listOf(
                WiremockFagsak(
                    eier = fnr(1), perioder =
                    listOf(
                        WiremockFagsak.BarnetrygdPeriode(
                            personIdent = fnr(2),
                            stønadFom = YearMonth.of(2022, 3),
                            stønadTom = YearMonth.of(2023, 10),
                            utbetaltPerMnd = 1000,
                        ),
                        WiremockFagsak.BarnetrygdPeriode(
                            personIdent = fnr(2),
                            stønadFom = YearMonth.of(2022, 5),
                            stønadTom = YearMonth.of(2023, 11),
                            utbetaltPerMnd = 2000,
                        ),
                    )
                ),
            )
        )

        wiremock.`hent hjelpestønad ok - ingen hjelpestønad`()

        val mottatt = Barnetrygdmottaker.Mottatt(
            id = UUID.randomUUID(),
            opprettet = Instant.now(),
            ident = fnr(1),
            personId = null,
            correlationId = CorrelationId.generate(),
            innlesingId = InnlesingId.generate(),
            statushistorikk = emptyList(),
            år = 2022,
        )


        val komplettert =
            Mdc.scopedMdc(mottatt.correlationId) {
                Mdc.scopedMdc(mottatt.innlesingId) {
                    kompletteringsService.kompletter(
                        mottatt
                    )
                }
            }

        assertThat(komplettert.feilinformasjon)
            .hasSize(1)
            .first()
            .isInstanceOf(Feilinformasjon.OverlappendeBarnetrygdperioder::class.java)
        println(komplettert)
        assertThat(komplettert.rådata).hasSizeGreaterThanOrEqualTo(1)  // TODO: Sette fast verdi igjen senere
        assertThat((komplettert.feilinformasjon.first() as Feilinformasjon.OverlappendeBarnetrygdperioder).omsorgsmottaker)
            .isEqualTo(fnr(2).value)
    }


    @Test
    fun `hjelpestønad har overlappende perioder`() {
        fun fnr(i: Int) = Ident(format("%011d", i))
        wiremock.pdl(fnr(1), listOf(fnr(1_1), fnr(1_2), fnr(1_3)))
        wiremock.pdl(fnr(2), listOf(fnr(2_1), fnr(2_2), fnr(2_3)))
        wiremock.pdl(fnr(3), listOf(fnr(3_1), fnr(3_2), fnr(3_3)))

        val fnrUtenBarnetrygdSaker =
            setOf(
                fnr(1), fnr(1_1),
            )
        fnrUtenBarnetrygdSaker.forEach {
            wiremock.`hent-barnetrygd ok uten fagsaker`(it)
        }

        wiremock.`hent-barnetrygd-med-fagsaker`(
            forFnr = fnr(1_2),
            fagsaker = listOf(
                WiremockFagsak(
                    eier = fnr(1_1), perioder =
                    listOf(
                        WiremockFagsak.BarnetrygdPeriode(
                            personIdent = fnr(2_1),
                            stønadFom = YearMonth.of(2020, 1),
                            stønadTom = YearMonth.of(2021, 12),
                        ),
                        WiremockFagsak.BarnetrygdPeriode(personIdent = fnr(2_2)),
                    )
                ),
                WiremockFagsak(
                    eier = fnr(1_2), perioder =
                    listOf(
                        WiremockFagsak.BarnetrygdPeriode(
                            personIdent = fnr(2_3),
                            stønadFom = YearMonth.of(2020, 2),
                            stønadTom = YearMonth.of(2020, 3),
                        ),
                    )
                ),
            )
        )
        wiremock.`hent-barnetrygd-med-fagsaker`(
            forFnr = fnr(1_3),
            fagsaker = listOf(
                WiremockFagsak(
                    eier = fnr(1_3), perioder =
                    listOf(
                        WiremockFagsak.BarnetrygdPeriode(personIdent = fnr(2_1)),
                        WiremockFagsak.BarnetrygdPeriode(personIdent = fnr(3_2)),
                    )
                ),
                WiremockFagsak(
                    eier = fnr(1_2), perioder =
                    listOf(
                        WiremockFagsak.BarnetrygdPeriode(personIdent = fnr(3_3)),
                    )
                ),
            )
        )

        val fnrUtenHjelpestønad = setOf(
            fnr(1), fnr(1_1), fnr(1_2), fnr(1_3),
            fnr(2), fnr(2_1), fnr(2_2), fnr(2_3),
            fnr(3), fnr(3_2),
        )
        fnrUtenHjelpestønad.forEach {
            wiremock.`hent hjelpestønad ok - ingen hjelpestønad`(it)
        }
        wiremock.`hent hjelpestønad ok - har hjelpestønad`(
            fnr(3_1),
            fom = YearMonth.of(2022, 2),
            tom = YearMonth.of(2023, 9),
            omsorgstype = "FORHØYET_SATS_3"
        )
        wiremock.`hent hjelpestønad ok - har hjelpestønad`(
            fnr(3_3),
            fom = YearMonth.of(2022, 4),
            tom = YearMonth.of(2023, 10),
            omsorgstype = "FORHØYET_SATS_4",
        )

        val mottatt = Barnetrygdmottaker.Mottatt(
            id = UUID.randomUUID(),
            opprettet = Instant.now(),
            ident = fnr(1_2),
            personId = null,
            correlationId = CorrelationId.generate(),
            innlesingId = InnlesingId.generate(),
            statushistorikk = emptyList(),
            år = 2022,
        )

        val komplettert =
            Mdc.scopedMdc(mottatt.correlationId) {
                Mdc.scopedMdc(mottatt.innlesingId) {
                    kompletteringsService.kompletter(
                        mottatt
                    )
                }
            }
        assertThat(komplettert.feilinformasjon)
            .hasSize(1)
            .first()
            .isInstanceOf(Feilinformasjon.OverlappendeHjelpestønadperioder::class.java)
        println(komplettert.feilinformasjon.first())

        assertThat(komplettert.persongrunnlag).isEmpty()
        assertThat(komplettert.rådata).hasSize(15) //  // TODO: Sette fast verdi igjen senere
    }

    @Test
    fun `barnetrygdmottaker mangler gjeldende ident`() {
        fun fnr(i: Int) = Ident(format("%011d", i))
        wiremock.`pdl - ingen gjeldende`(listOf(fnr(1)))
        wiremock.`hent-barnetrygd-med-fagsaker`(
            forFnr = fnr(1),
            fagsaker = listOf(
                WiremockFagsak(
                    eier = fnr(1), perioder =
                    listOf(
                        WiremockFagsak.BarnetrygdPeriode(
                            personIdent = fnr(2),
                            stønadFom = YearMonth.of(2022, 3),
                            stønadTom = YearMonth.of(2023, 10),
                            utbetaltPerMnd = 1000,
                        ),
                    )
                ),
            )
        )

        wiremock.`hent hjelpestønad ok - ingen hjelpestønad`()

        val mottatt = Barnetrygdmottaker.Mottatt(
            id = UUID.randomUUID(),
            opprettet = Instant.now(),
            ident = fnr(1),
            personId = null,
            correlationId = CorrelationId.generate(),
            innlesingId = InnlesingId.generate(),
            statushistorikk = emptyList(),
            år = 2022,
        )


        val komplettert =
            Mdc.scopedMdc(mottatt.correlationId) {
                Mdc.scopedMdc(mottatt.innlesingId) {
                    kompletteringsService.kompletter(
                        mottatt
                    )
                }
            }
        assertThat(komplettert.feilinformasjon)
            .hasSize(1)
            .first()
            .isInstanceOf(Feilinformasjon.UgyldigIdent::class.java)
        assertThat(komplettert.rådata.isEmpty())
    }

    @Test
    fun `omsorgsmottaker for barnetrygd mangler gjeldende ident`() {
        fun fnr(i: Int) = Ident(format("%011d", i))
        wiremock.pdl(fnr(1), emptyList())
        wiremock.`pdl - ingen gjeldende`(listOf(fnr(2)))
        wiremock.`hent-barnetrygd-med-fagsaker`(
            forFnr = fnr(1),
            fagsaker = listOf(
                WiremockFagsak(
                    eier = fnr(1), perioder =
                    listOf(
                        WiremockFagsak.BarnetrygdPeriode(
                            personIdent = fnr(2),
                            stønadFom = YearMonth.of(2022, 3),
                            stønadTom = YearMonth.of(2023, 10),
                            utbetaltPerMnd = 1000,
                        ),
                    )
                ),
            )
        )

        wiremock.`hent hjelpestønad ok - ingen hjelpestønad`()

        val mottatt = Barnetrygdmottaker.Mottatt(
            id = UUID.randomUUID(),
            opprettet = Instant.now(),
            ident = fnr(1),
            personId = null,
            correlationId = CorrelationId.generate(),
            innlesingId = InnlesingId.generate(),
            statushistorikk = emptyList(),
            år = 2022,
        )

        val komplettert =
            Mdc.scopedMdc(mottatt.correlationId) {
                Mdc.scopedMdc(mottatt.innlesingId) {
                    kompletteringsService.kompletter(
                        mottatt
                    )
                }
            }
        assertThat(komplettert.feilinformasjon)
            .hasSize(1)
            .first()
            .isInstanceOf(Feilinformasjon.UgyldigIdent::class.java)
        assertThat(komplettert.rådata)
            .filteredOn { it.containsKey("barnetrygd") }
            .hasSize(1)
        assertThat(komplettert.rådata)
            .filteredOn { it.containsKey(fnr(1).value) }
            .hasSize(1)
    }

    private fun persongrunnlag(
        omsorgsyter: String,
        omsorgsmottaker: String,
        år: Int,
    ) = PersongrunnlagMelding.Persongrunnlag(
        omsorgsyter = omsorgsyter,
        omsorgsperioder = listOf(
            omsorgsperiode(år, omsorgsmottaker)
        ),
        hjelpestønadsperioder = emptyList()
    )

    private fun omsorgsperiode(år: Int, omsorgsmottaker: String) = PersongrunnlagMelding.Omsorgsperiode(
        fom = YearMonth.of(år, 1),
        tom = YearMonth.of(år, 12),
        omsorgstype = Omsorgstype.FULL_BARNETRYGD,
        omsorgsmottaker = omsorgsmottaker,
        kilde = Kilde.BARNETRYGD,
        utbetalt = 2000,
        landstilknytning = Landstilknytning.NORGE,
    )

}