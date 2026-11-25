package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.web

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.SpringContextTest
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdInnlesing
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Barnetrygdmottaker
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdmottakerService
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Ident
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.År
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.web.dto.FeiletMottakerResponse
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.web.dto.InnlesingResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.*
import org.mockito.kotlin.given
import java.time.Instant

class AdminWebApiTest : SpringContextTest.NoKafka() {

    @Autowired
    private lateinit var adminWebApi: AdminWebApi

    @MockitoBean
    private lateinit var barnetrygdmottakerService: BarnetrygdmottakerService

    @Test
    fun `kan parse en liste med UUIDer`() {
        val uuids = adminWebApi.parseUUIDListe("\"a7368db4-ecf2-494a-a7aa-95b9363ad0a0\",")
        assertThat(uuids)
            .hasSize(1)
            .contains(UUID.fromString("a7368db4-ecf2-494a-a7aa-95b9363ad0a0"))
    }

    @Test
    fun `kan hente feilede mottakere for innlesning`() {
        val innlesingId = UUID.fromString("f9792c4b-76ed-4906-ab06-caea51b3bf3b")
        val mottakerId = UUID.fromString("170f5d04-36ac-4457-8473-53f38201a0fc")
        val feiletTidspunkt = Instant.parse("2026-01-02T03:04:05Z")
        val feiletStatus = Barnetrygdmottaker.Status.Feilet(tidspunkt = feiletTidspunkt)
        given(barnetrygdmottakerService.hentFeiledeMottakereMedInformasjon(innlesingId)).willReturn(
            listOf(
                Barnetrygdmottaker.Mottatt(
                    id = mottakerId,
                    opprettet = Instant.now(),
                    ident = Ident("12345678901"),
                    correlationId = CorrelationId.generate(),
                    innlesingId = InnlesingId.fromString(innlesingId.toString()),
                    statushistorikk = listOf(feiletStatus),
                    år = 2023
                ) to null
            )
        )

        val response = adminWebApi.hentFeiledeMottakereMedInformasjon(innlesingId)
        assertThat(response.body).containsExactly(
            FeiletMottakerResponse(
                mottakerId = mottakerId,
                opptjeningsAr = 2023,
                status = "Feilet",
            )
        )
    }

    @Test
    fun `kan hente innlesinger`() {
        val innlesingId = InnlesingId.fromString("f9792c4b-76ed-4906-ab06-caea51b3bf3b")
        val forespurt = Instant.parse("2026-01-02T03:04:05Z")
        val startet = Instant.parse("2026-01-02T04:04:05Z")
        val ferdig = Instant.parse("2026-01-02T05:04:05Z")
        given(barnetrygdmottakerService.hentInnlesinger()).willReturn(
            listOf(
                BarnetrygdInnlesing.Ferdig(
                    id = innlesingId,
                    år = År(2023),
                    forespurtTidspunkt = forespurt,
                    startTidspunkt = startet,
                    ferdigTidspunkt = ferdig,
                    antallIdenterLest = 12,
                    forventetAntallIdentiteter = 13,
                )
            )
        )

        val response = adminWebApi.hentInnlesinger()
        assertThat(response.body).containsExactly(
            InnlesingResponse(
                id = innlesingId.toUUID(),
                år = 2023,
                status = "FERDIG",
                forespurtTidspunkt = forespurt,
                startTidspunkt = startet,
                ferdigTidspunkt = ferdig,
                forventetAntallIdentiteter = 13,
                antallIdenterLest = 12,
            )
        )
    }
}