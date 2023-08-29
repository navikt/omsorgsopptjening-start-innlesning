package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import com.github.tomakehurst.wiremock.matching.AnythingPattern
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.deserialize
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.OmsorgsgrunnlagMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.serialize
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Innlesing
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.InnlesingRepo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.util.UUID

class KafkaIntegrationTest : SpringContextTest.WithKafka() {

    @Autowired
    private lateinit var listener: OmsorgsopptjeningTopicListener

    @Autowired
    private lateinit var innlesingRepo: InnlesingRepo

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
                .withHeader(CorrelationId.identifier, AnythingPattern())
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

        val innlesing = innlesingRepo.bestilt(Innlesing(InnlesingId.generate(), 2020))
        sendStartInnlesingKafka(innlesing.id.toString())
        sendBarnetrygdmottakerDataKafka(
            melding = KafkaMelding(
                meldingstype = KafkaMelding.Type.DATA,
                requestId = UUID.fromString(innlesing.id.toString()),
                personident = "12345678910"
            )
        )
        sendSluttInnlesingKafka(innlesing.id.toString())

        listener.removeFirstRecord(5).let {
            assertEquals(
                """
                    {"ident":"12345678910"}
                """.trimIndent(),
                it.key()
            )
            deserialize<OmsorgsgrunnlagMelding>(it.value()).also {
                assertEquals("12345678910", it.omsorgsyter)
                assertEquals("BARNETRYGD", it.omsorgstype.toString())
                assertEquals("BARNETRYGD", it.kilde.toString())
                assertEquals(
                    listOf(
                        OmsorgsgrunnlagMelding.Sak(
                            omsorgsyter = "12345678910",
                            vedtaksperioder = emptyList()
                        )
                    ),
                    it.saker
                )
                assertEquals(
                    """[{"fagsakId":"1","fagsakEiersIdent":"12345678910","barnetrygdPerioder":[]}]""",
                    it.rådata.toString()
                )
                assertEquals(innlesing.id, it.innlesingId)
                assertNotNull(it.correlationId) //opprettes internt
            }
        }
    }
}