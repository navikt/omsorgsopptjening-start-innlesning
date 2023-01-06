package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.health

import no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.App
import no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.databasecontainer.TestDatabaseOppsett
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.testcontainers.containers.PostgreSQLContainer


@SpringBootTest(classes = [App::class])
@AutoConfigureMockMvc
@EnableMockOAuth2Server
class HealthControllerTest: TestDatabaseOppsett() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `when calling ping endpoint then return 200`() {
        mockMvc.perform(MockMvcRequestBuilders.get("/ping"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn()
    }

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