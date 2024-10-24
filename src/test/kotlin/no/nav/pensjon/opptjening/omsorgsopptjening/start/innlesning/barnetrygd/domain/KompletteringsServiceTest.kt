package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.RådataFraKilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Kilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Landstilknytning
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Omsorgstype
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.PersongrunnlagMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.serialize
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Mdc
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.SpringContextTest
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.barnetrygd.WiremockFagsak
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.barnetrygd.`hent-barnetrygd ok uten fagsaker`
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.barnetrygd.`hent-barnetrygd-med-fagsaker`
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.hjelpestønad.`hent hjelpestønad ok - har hjelpestønad`
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.hjelpestønad.`hent hjelpestønad ok - ingen hjelpestønad`
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.pdl.pdl
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.pdl.`pdl fnr fra query`
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import java.lang.String.format
import java.time.Instant
import java.time.YearMonth
import java.util.*


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
        assertThat(oppdatertBarnetrygdData).isEqualTo(barnetrygdData)
    }

    // @Disabled // TODO: midlertidig
    @Test
    fun `full flyt med fnr-historikk og oppdatering`() {
        fun fnr(i: Int) = format("%011d", i)
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

        println("HELLO")

        println(komplettert)

        println("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX")

        println("+++ SERIALIZED:persongrunnlag:")
        println(serialize(komplettert.persongrunnlag))
        println("+++ SERIALIZED:rådata:")
        println(serialize(komplettert.rådata))
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