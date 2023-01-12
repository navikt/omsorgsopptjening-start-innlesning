package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.start

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.App
import no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.databasecontainer.PostgresqlTestContainer
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
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import kotlin.test.assertEquals


@SpringBootTest(classes = [App::class])
@AutoConfigureMockMvc
@EnableMockOAuth2Server
class StartInnlesning {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var server: MockOAuth2Server

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var repository: StartHistorikkRepository

    private val dbContainer = PostgresqlTestContainer.instance

    @BeforeEach
    fun resetWiremock() {
        wiremock.resetAll()
        repository.deleteAll()
    }

    @Test
    fun `Given valid request When calling start innlesning Then return 200 ok`() {
        wiremock.stubFor(WireMock.get(BA_START_INNLESNING_URL).willReturn(WireMock.aResponse().withStatus(200)))

        callStartInnelsning(ar = AR_2010).andExpect(MockMvcResultMatchers.status().isOk)
    }

    @Test
    fun `Given valid request When calling start innlesning Then call BA start innlesning`() {
        wiremock.stubFor(WireMock.get(BA_START_INNLESNING_URL).willReturn(WireMock.aResponse().withStatus(200)))

        callStartInnelsning(ar = AR_2010).andExpect(MockMvcResultMatchers.status().isOk)

        wiremock.verify(1, WireMock.getRequestedFor(WireMock.urlEqualTo(BA_START_INNLESNING_URL)))
    }

    @Test
    fun `Given invalid token When calling get start innlesning Then return 401 unauthorized`() {
        callStartInnelsning(ar = AR_2010, token = createToken(audience = "invalid")).andExpect(MockMvcResultMatchers.status().isUnauthorized)
    }

    @Test
    fun `Given ar not provided When calling get start innlesning Then return 404 not found`() {
        callStartInnelsning(ar = null).andExpect(MockMvcResultMatchers.status().isNotFound)
    }

    @Test
    fun `Given no calls to started innlesning When calling start innlesning historikk Then empty list`() {
        wiremock.stubFor(WireMock.get(BA_START_INNLESNING_URL).willReturn(WireMock.aResponse().withStatus(200)))

        val response = callInnlesningsHistorikk()
            .andExpect(MockMvcResultMatchers.status().isOk)
            .getHistorikkFromBody()

        assertEquals(0, response.size)
    }

    @Test
    fun `Given started innlesning When calling start innlesning historikk Then return historikk`() {
        wiremock.stubFor(WireMock.get(BA_START_INNLESNING_URL).willReturn(WireMock.aResponse().withStatus(200)))

        callStartInnelsning(ar = AR_2010).andExpect(MockMvcResultMatchers.status().isOk)

        val response = callInnlesningsHistorikk()
            .andExpect(MockMvcResultMatchers.status().isOk)
            .getHistorikkFromBody()

        assertEquals(1, response.size)
        assertEquals(AR_2010.toString(), response.first().kjoringsAr)
    }

    @Test
    fun `Given invalid token When calling start innlesning historikk Then return 401`() {
        wiremock.stubFor(WireMock.get(BA_START_INNLESNING_URL).willReturn(WireMock.aResponse().withStatus(200)))

        callStartInnelsning(ar = AR_2010,token = createToken(audience = "unauthorized"))
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)
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

    private fun callStartInnelsning(ar: Int?, token: String? = createToken()) =
        mockMvc.perform(
            MockMvcRequestBuilders.get("/start/innlesning/${ar ?: ""}").apply {
                contentType(MediaType.APPLICATION_JSON)
                token?.let { header(HttpHeaders.AUTHORIZATION, token) }
            }
        )

    private fun callInnlesningsHistorikk() =
        mockMvc.perform(
            MockMvcRequestBuilders.get("/start/innlesning/historikk")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, createToken())
        )

    private fun ResultActions.getHistorikkFromBody() = objectMapper.readValue(
        this.andReturn().response.contentAsString,
        object : TypeReference<List<StartHistorikk>>() {}
    )

    companion object {
        private const val ACCEPTED_AUDIENCE = "testaud"
        private const val AR_2010 = 2010

        private val wiremock = WireMockServer(WireMockSpring.options().port(9991)).also { it.start() }

        private const val BA_START_INNLESNING_URL = "/omsorg/arbeid/start"

        @JvmStatic
        @AfterAll
        fun clean() {
            wiremock.stop()
            wiremock.shutdown()
        }
    }
}