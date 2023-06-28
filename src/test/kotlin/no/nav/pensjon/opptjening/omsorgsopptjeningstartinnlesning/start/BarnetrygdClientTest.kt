package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.start

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired

class BarnetrygdClientTest : SpringContextTest.NoKafka() {

    @Autowired
    private lateinit var client: BarnetrygdClient

    companion object {
        @RegisterExtension
        private val wiremock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
            .build()!!
    }

    @Test
    fun `returner ok dersom kall til hent-barnetrygdmottakere går bra`() {
        wiremock.stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/api/ekstern/pensjon/hent-barnetrygdmottakere"))
                .willReturn(WireMock.ok())
        )

        client.initierSendingAvIdenter(2020).also {
            assertEquals(BarnetrygdClientResponse.Ok(null), it)
        }
    }

    @Test
    fun `returner feil dersom kall til hent-barnetrygdmottakere går dårlig`() {
        wiremock.stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/api/ekstern/pensjon/hent-barnetrygdmottakere"))
                .willReturn(WireMock.serverError().withBody("Feilmelding"))
        )

        client.initierSendingAvIdenter(2020).also {
            assertEquals(BarnetrygdClientResponse.Feil(500, "Feilmelding"), it)
        }
    }

    @Test
    fun `returner ok dersom kall til hent-barnetrygd går bra`() {
        wiremock.stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/api/ekstern/pensjon/hent-barnetrygd"))
                .willReturn(WireMock.ok().withBody("""{"json":{"key":"value"}}"""))
        )

        client.hentBarnetrygdDetaljer("123", 2020).also {
            assertEquals(BarnetrygdClientResponse.Ok("""{"json":{"key":"value"}}"""), it)
        }
    }

    @Test
    fun `returner feil dersom kall til hent-barnetrygd går dårlig`() {
        wiremock.stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/api/ekstern/pensjon/hent-barnetrygd"))
                .willReturn(WireMock.serverError().withBody("Feilmelding"))
        )

        client.hentBarnetrygdDetaljer("123", 2020).also {
            assertEquals(BarnetrygdClientResponse.Feil(500, "Feilmelding"), it)
        }
    }
}