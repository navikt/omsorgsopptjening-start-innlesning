package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.web

import com.nimbusds.jose.JOSEObjectType
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdInnlesing
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdmottakerService
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
@EnableMockOAuth2Server
class PrometheusWebApiTest {

    @Autowired
    private lateinit var oauth2Server: MockOAuth2Server

    @Autowired
    private lateinit var mvc: MockMvc

    @Test
    fun `prometheus-url'en finnes og svarer`() {
        mvc.perform(
            get("/actuator/prometheus")
        ).andExpect(status().isOk())
    }


    private fun token(
        issuerId: String,
        audience: String
    ): String {
        return "Bearer " + oauth2Server.issueToken(
            issuerId,
            "theclientid",
            DefaultOAuth2TokenCallback(
                issuerId,
                "subject",
                JOSEObjectType.JWT.type,
                listOf(audience), emptyMap(),
                3600
            )
        ).serialize()
    }
}