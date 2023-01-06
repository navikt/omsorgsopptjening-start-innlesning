package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.start

import no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.App
import no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.databasecontainer.PostgresqlTestContainer
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers


@SpringBootTest(classes = [App::class])
@AutoConfigureMockMvc
@EnableMockOAuth2Server
class StartInnlesningControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var server: MockOAuth2Server

    private val dbContainer = PostgresqlTestContainer.instance

    @Test
    fun `Given valid token When calling get start innlesning Then return 200 ok`() {
        val ar = 2010

        mockMvc.perform(
            MockMvcRequestBuilders.get("/start/innlesning/$ar")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, createToken())
        ).andExpect(MockMvcResultMatchers.status().isOk)
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
    }
}