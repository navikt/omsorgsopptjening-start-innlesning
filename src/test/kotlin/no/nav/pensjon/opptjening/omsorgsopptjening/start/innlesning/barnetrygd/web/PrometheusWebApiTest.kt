package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.web

import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@EnableMockOAuth2Server
class PrometheusWebApiTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Disabled
    @Test
    fun `prometheus-url'en finnes og svarer`() {
        mvc.perform(
            get("/actuator/prometheus")
        ).andExpect(status().isOk())
    }

    @Disabled
    @Test
    fun `metrics-url'en finnes og svarer`() {
        mvc.perform(
            get("/actuator/metrics")
        ).andExpect(status().isOk())
    }

    @Disabled
    @Test
    fun `prometheus-url'en returnerer json`() {
        mvc.perform(
            get("/actuator/prometheus")
        ).andExpect(content().contentType("application/json"))
    }

    @Disabled
    @Test
    fun `prometheus-url'en returnerer faktisk json`() {
        val body = mvc.perform(
            get("/actuator/prometheus")
        ).andReturn().response.contentAsString
        assertThat(body)
            .startsWith("{")
            .contains("omsorgsopptjening-start-innlesning")
    }
}