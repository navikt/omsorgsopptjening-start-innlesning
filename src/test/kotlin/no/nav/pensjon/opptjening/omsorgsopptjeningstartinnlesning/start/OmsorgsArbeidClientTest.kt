package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.start


import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.App
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.WireMockSpring
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers


@SpringBootTest(classes = [App::class])
@AutoConfigureMockMvc
@EnableMockOAuth2Server
internal class OmsorgsArbeidClientTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var server: MockOAuth2Server

    @BeforeEach
    fun resetWiremock() {
        wiremock.resetAll()
    }


    @Test
    fun `Given When Then`() {
        wiremock.stubFor(WireMock.get(START_INNLESNING_URL).willReturn(WireMock.aResponse().withStatus(200)))

        mockMvc.perform(
            MockMvcRequestBuilders.get("/start/innlesning/$AR")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, createToken())
        ).andExpect(MockMvcResultMatchers.status().isOk)

        wiremock.verify(1 , WireMock.getRequestedFor( WireMock.urlEqualTo(START_INNLESNING_URL)))
    }

    private fun createToken(audience: String = ACCEPTED_AUDIENCE): String {
        return "Bearer ${
            server.issueToken(
                issuerId = "aad",
                clientId = "client",
                tokenCallback = DefaultOAuth2TokenCallback(issuerId = "aad", audience = listOf(audience))
            ).serialize()
        }"
    }


    companion object {
        private const val ACCEPTED_AUDIENCE = "testaud"
        private const val AR = 2010

        private val wiremock = WireMockServer(WireMockSpring.options().port(9991)).also { it.start() }

        private const val START_INNLESNING_URL = "/omsorg/arbeid/start"

        @JvmStatic
        @AfterAll
        fun clean() {
            wiremock.stop()
            wiremock.shutdown()
        }
    }

}