package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.health

import no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.App
import no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.databasecontainer.PostgresqlTestContainer
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers


@SpringBootTest(classes = [App::class])
@AutoConfigureMockMvc
@EnableMockOAuth2Server
class HealthControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private val dbContainer = PostgresqlTestContainer.instance

    @Test
    fun `when isAlive ping endpoint then return 200`() {
        mockMvc.perform(MockMvcRequestBuilders.get("/internal/isalive"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn()
    }

    @Test
    fun `when calling isReady endpoint then return 200`() {
        mockMvc.perform(MockMvcRequestBuilders.get("/internal/isready"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn()
    }
}