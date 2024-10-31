package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.pdl

import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Mdc
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.SpringContextTest
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Ident
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.pdl.PdlService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired

class WiremockScenarioTest : SpringContextTest.NoKafka() {

    companion object {
        @JvmField
        @RegisterExtension
        val wiremock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT))
            .build()!!
    }

    @Autowired
    lateinit var pdlService: PdlService

    @Test
    fun `wiremock fnr fra query returnerer riktig fnr`() {
        val fnr = Ident("91929394959")
        wiremock.`pdl fnr fra query`()

        val person =
            Mdc.scopedMdc(CorrelationId.generate()) {
                Mdc.scopedMdc(InnlesingId.generate()) {
                    pdlService.hentPerson(fnr)
                }
            }
        assertThat(person.fnr).isEqualTo(fnr)
    }
}