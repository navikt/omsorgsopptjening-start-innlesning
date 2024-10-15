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
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Mdc
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.SpringContextTest
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.pdl.`pdl fnr fra query`
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import java.time.YearMonth


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
    fun `BarnetrygdData med én barnetrygdsak`() {
        val sak = persongrunnlag("12345678901", "12345678920", 2018)
        val response = HentBarnetrygdResponse(
            barnetrygdsaker = listOf(
                sak
            ),
            rådataFraKilde = RådataFraKilde(emptyMap())
        )

        val barnetrygdData = KompletteringsService.BarnetrygdData(
            responses = listOf(response)
        )
        val saker = barnetrygdData.getSanitizedBarnetrygdSaker()
        assertThat(saker).containsOnly(sak)
    }

    @Test
    fun `BarnetrygdData med flere barnetrygdsaker for ulike omsorgsytere med ulike perioder`() {
        val sak1 = persongrunnlag("12345678901", "12345678920", 2018)
        val sak2 = persongrunnlag("12345678902", "12345678920", 2019)
        val response = HentBarnetrygdResponse(
            barnetrygdsaker = listOf(
                sak1, sak2
            ),
            rådataFraKilde = RådataFraKilde(emptyMap())
        )

        val barnetrygdData = KompletteringsService.BarnetrygdData(
            responses = listOf(response)
        )
        val saker = barnetrygdData.getSanitizedBarnetrygdSaker()
        assertThat(saker).containsExactly(sak1, sak2)
    }

    @Test
    fun `BarnetrygdData med flere forekomster av samme barnetrygdsak`() {
        val sak = persongrunnlag("12345678901", "12345678920", 2018)
        val response = HentBarnetrygdResponse(
            barnetrygdsaker = listOf(
                sak, sak,
            ),
            rådataFraKilde = RådataFraKilde(emptyMap())
        )

        val barnetrygdData = KompletteringsService.BarnetrygdData(
            responses = listOf(response)
        )
        val saker = barnetrygdData.getSanitizedBarnetrygdSaker()
        assertThat(saker).containsExactly(sak)
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

        val barnetrygdData = KompletteringsService.BarnetrygdData(
            responses = listOf(response1, response2)
        )

        val oppdatertBarnetrygdData =
            Mdc.scopedMdc(CorrelationId.generate()) {
                Mdc.scopedMdc(InnlesingId.generate()) {
                    kompletteringsService.oppdaterAlleFnr(barnetrygdData)
                }
            }
        assertThat(oppdatertBarnetrygdData).isEqualTo(barnetrygdData)
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