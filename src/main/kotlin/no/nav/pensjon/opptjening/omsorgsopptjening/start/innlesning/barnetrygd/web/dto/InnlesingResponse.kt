package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.web.dto

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdInnlesing
import java.time.Instant
import java.util.UUID

data class InnlesingResponse(
    val id: UUID,
    val år: Int,
    val status: String,
    val forespurtTidspunkt: Instant,
    val startTidspunkt: Instant?,
    val ferdigTidspunkt: Instant?,
    val forventetAntallIdentiteter: Long?,
    val antallIdenterLest: Int?,
) {
    companion object {
        fun from(innlesing: BarnetrygdInnlesing): InnlesingResponse {
            return when (innlesing) {
                is BarnetrygdInnlesing.Bestilt -> InnlesingResponse(
                    id = innlesing.id.toUUID(),
                    år = innlesing.år.value,
                    status = "BESTILT",
                    forespurtTidspunkt = innlesing.forespurtTidspunkt,
                    startTidspunkt = null,
                    ferdigTidspunkt = null,
                    forventetAntallIdentiteter = null,
                    antallIdenterLest = null,
                )

                is BarnetrygdInnlesing.Startet -> InnlesingResponse(
                    id = innlesing.id.toUUID(),
                    år = innlesing.år.value,
                    status = "STARTET",
                    forespurtTidspunkt = innlesing.forespurtTidspunkt,
                    startTidspunkt = innlesing.startTidspunkt,
                    ferdigTidspunkt = null,
                    forventetAntallIdentiteter = innlesing.forventetAntallIdentiteter,
                    antallIdenterLest = innlesing.antallIdenterLest,
                )

                is BarnetrygdInnlesing.Ferdig -> InnlesingResponse(
                    id = innlesing.id.toUUID(),
                    år = innlesing.år.value,
                    status = "FERDIG",
                    forespurtTidspunkt = innlesing.forespurtTidspunkt,
                    startTidspunkt = innlesing.startTidspunkt,
                    ferdigTidspunkt = innlesing.ferdigTidspunkt,
                    forventetAntallIdentiteter = innlesing.forventetAntallIdentiteter,
                    antallIdenterLest = innlesing.antallIdenterLest,
                )
            }
        }
    }
}
