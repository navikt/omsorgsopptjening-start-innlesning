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
class BarnetrygdWebApiTest {

    @Autowired
    private lateinit var oauth2Server: MockOAuth2Server

    @Autowired
    private lateinit var mvc: MockMvc

    @MockBean
    private lateinit var barnetrygdmottakerService: BarnetrygdmottakerService

    @Test
    fun `svarer med 401 ingen token`() {
        mvc.perform(
            get("/innlesning/start/2022")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `svarer med 401 hvis issuer er feil`() {
        mvc.perform(
            get("/innlesning/start/2022")
                .header(HttpHeaders.AUTHORIZATION, token("okta", "appClientId"))
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `svarer med 401 hvis audience er feil`() {
        mvc.perform(
            get("/innlesning/start/2022")
                .header(HttpHeaders.AUTHORIZATION, token("azure", "nicht gut"))
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `svarer med 200 hvis gyldig token`() {
        given(barnetrygdmottakerService.bestillPersonerMedBarnetrygd(any())).willReturn(
            BarnetrygdInnlesing.Bestilt(
                id = InnlesingId.fromString("f9792c4b-76ed-4906-ab06-caea51b3bf3b"),
                år = 2022,
                forespurtTidspunkt = Instant.now(),
            )
        )
        mvc.perform(
            get("/innlesning/start/2022")
                .header(HttpHeaders.AUTHORIZATION, token("azure", "appClientId"))
        ).andExpect(status().isOk)
            .andExpect(
                content().string("f9792c4b-76ed-4906-ab06-caea51b3bf3b")
            )
    }

    @Test
    fun `kan kalle actuator uten token`() {
        mvc.perform(
            get("/actuator/health")
        ).andExpect(status().isOk)
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