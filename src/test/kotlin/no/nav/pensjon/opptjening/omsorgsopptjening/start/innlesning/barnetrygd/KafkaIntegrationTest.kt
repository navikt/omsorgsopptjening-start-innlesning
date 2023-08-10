package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import com.github.tomakehurst.wiremock.matching.AnythingPattern
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.KafkaMessageType
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.barnetrygd.Barnetrygdmelding
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.serialize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType

class KafkaIntegrationTest : SpringContextTest.WithKafka() {

    @Autowired
    lateinit var listener: OmsorgsopptjeningTopicListener

    companion object {
        @RegisterExtension
        private val wiremock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
            .build()!!
    }

    @Test
    fun test() {
        wiremock.stubFor(
            WireMock.post(urlPathEqualTo("/api/ekstern/pensjon/hent-barnetrygd"))
                .withHeader(CorrelationId.name, AnythingPattern())
                .withHeader(HttpHeaders.CONTENT_TYPE, WireMock.equalTo(MediaType.APPLICATION_JSON_VALUE))
                .withHeader(HttpHeaders.ACCEPT, WireMock.equalTo(MediaType.APPLICATION_JSON_VALUE))
                .withHeader(HttpHeaders.AUTHORIZATION, WireMock.equalTo("Bearer test.token.test"))
                .willReturn(
                    WireMock.ok()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(
                            serialize(
                                listOf(
                                    Barnetrygdmelding.Sak(
                                        fagsakId = "1",
                                        fagsakEiersIdent = "12345678910",
                                        barnetrygdPerioder = emptyList()
                                    )
                                )
                            )
                        )
                )

        )

        sendBarnetrygdMottakerKafka(BarnetrygdmottakerKafkaListener.KafkaMelding("12345678910", 20))

        listener.removeFirstRecord(5).let {
            assertEquals(
                """
                    {"ident":"12345678910"}
                """.trimIndent(),
                it.key()
            )
            assertEquals(
                """
                    {"omsorgsyter":"12345678910","omsorgstype":"BARNETRYGD","kjoreHash":"xxx","kilde":"BARNETRYGD","saker":[{"omsorgsyter":"12345678910","vedtaksperioder":[]}],"rådata":{"data":"[{\"fagsakId\":\"1\",\"fagsakEiersIdent\":\"12345678910\",\"barnetrygdPerioder\":[]}]"}}
                """.trimIndent(),
                it.value()
            )
            assertEquals(
                "OMSORGSGRUNNLAG",
                String(it.headers().lastHeader(KafkaMessageType.name).value())
            )
            assertNotNull(String(it.headers().lastHeader(CorrelationId.name).value()))
        }
    }
}