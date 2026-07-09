package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.hjelpestønad

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Mdc
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.SpringContextTest
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Ident
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.hjelpestønad.HjelpestønadClient
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.hjelpestønad.HjelpestønadType
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.hjelpestønad.HjelpestønadVedtak
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.YearMonth

/**
 * Verifiserer at HjelpestønadClient beheld original oppførsel etter Spring Boot 4-migreringa
 * (WebClient -> RestTemplate). Original brukte onStatus(not200){ Mono.empty() }: alle svar,
 * uansett statuskode, blir mata inn i deserialiser-eller-tom-logikken – klienten kaster ALDRI
 * HttpClientErrorException/HttpServerErrorException sjølv. Testane under låser den kontrakten.
 */
internal class HjelpestønadClientTest : SpringContextTest.NoKafka() {

    @Autowired
    private lateinit var client: HjelpestønadClient

    companion object {
        val FNR = Ident("12345678901")

        @JvmField
        @RegisterExtension
        val wiremock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
            .build()!!
    }

    private fun kall() = Mdc.scopedMdc(CorrelationId.generate()) {
        Mdc.scopedMdc(InnlesingId.generate()) {
            client.hentHjelpestønad(FNR, LocalDate.of(2020, 1, 1), LocalDate.of(2025, 12, 31))
        }
    }

    @Test
    fun `200 med hjelpestønad - parser vedtaket`() {
        wiremock.`hent hjelpestønad ok - har hjelpestønad`(FNR)

        val response = kall()

        assertThat(response.vedtak).containsExactly(
            HjelpestønadVedtak(
                id = 123,
                ident = FNR.value,
                fom = YearMonth.of(2020, 1),
                tom = YearMonth.of(2025, 12),
                omsorgstype = HjelpestønadType.FORHØYET_SATS_3,
            )
        )
    }

    @Test
    fun `200 med tom liste - gir tomt vedtak`() {
        wiremock.`hent hjelpestønad ok - ingen hjelpestønad`(FNR)

        assertThat(kall().vedtak).isEmpty()
    }

    @Test
    fun `4xx kaster ikke - beheld forgiving onStatus-oppførsel`() {
        // Migreringas mellomversjon kasta HttpClientErrorException her; original gjorde det ikkje.
        wiremock.stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/api/hjelpestonad/hent")).willReturn(
                WireMock.aResponse().withStatus(404)
                    .withHeader("Content-Type", "application/json").withBody("[]")
            )
        )

        assertThatCode { assertThat(kall().vedtak).isEmpty() }.doesNotThrowAnyException()
    }

    @Test
    fun `5xx kaster ikke - beheld forgiving onStatus-oppførsel`() {
        // Migreringas mellomversjon lot HttpServerErrorException boble ut; original gjorde det ikkje.
        wiremock.stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/api/hjelpestonad/hent")).willReturn(
                WireMock.aResponse().withStatus(500)
                    .withHeader("Content-Type", "application/json").withBody("[]")
            )
        )

        assertThatCode { assertThat(kall().vedtak).isEmpty() }.doesNotThrowAnyException()
    }
}
