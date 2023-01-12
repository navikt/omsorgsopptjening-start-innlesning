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
class StartInnlesningControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var server: MockOAuth2Server

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val dbContainer = PostgresqlTestContainer.instance

    @BeforeEach
    fun resetWiremock() {
        wiremock.resetAll()
    }

    @Test
    fun `Given valid token When calling get start innlesning Then return 200 ok`() {
        wiremock.stubFor(WireMock.get(BA_START_INNLESNING_URL).willReturn(WireMock.aResponse().withStatus(200)))

        mockMvc.perform(
            MockMvcRequestBuilders.get("/start/innlesning/$AR")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, createToken())
        ).andExpect(MockMvcResultMatchers.status().isOk)

        val response = mockMvc.perform(
            MockMvcRequestBuilders.get("/start/innlesning/historikk")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, createToken())
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .hentStartHistorikk()


        assertEquals(AR.toString(), response.first().kjoringsAr)
    }

    @Test
    fun `Given valid request When calling start innlesning Then call BA start innlesning`() {
        wiremock.stubFor(WireMock.get(BA_START_INNLESNING_URL).willReturn(WireMock.aResponse().withStatus(200)))

        mockMvc.perform(
            MockMvcRequestBuilders.get("/start/innlesning/${AR}")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, createToken())
        ).andExpect(MockMvcResultMatchers.status().isOk)

        wiremock.verify(1 , WireMock.getRequestedFor(WireMock.urlEqualTo(
            BA_START_INNLESNING_URL
        )))
    }


    @Test
    fun `Given invalid token When calling get start innlesning Then return 401 unauthorized`() {
        val ar = 2010

        mockMvc.perform(
            MockMvcRequestBuilders.get("/start/innlesning/$ar")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, createToken(audience = "invalid"))
        ).andExpect(MockMvcResultMatchers.status().isUnauthorized)
    }

    @Test
    fun `Given ar not provided When calling get start innlesning Then return 404 not found`() {
        mockMvc.perform(
            MockMvcRequestBuilders.get("/start/innlesning/")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, createToken())
        ).andExpect(MockMvcResultMatchers.status().isNotFound)
    }

    private fun ResultActions.hentStartHistorikk() = objectMapper.readValue(
        this.andReturn().response.contentAsString,
        object : TypeReference<List<StartHistorikk>>() {}
    )

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

        private const val BA_START_INNLESNING_URL = "/omsorg/arbeid/start"

        @JvmStatic
        @AfterAll
        fun clean() {
            wiremock.stop()
            wiremock.shutdown()
        }
    }
}