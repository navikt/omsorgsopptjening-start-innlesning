package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import java.time.Instant

data class Innlesing(
    val id: InnlesingId,
    val år: Int,
    val forespurtTidspunkt: Instant? = null,
    val startTidspunkt: Instant? = null,
    val ferdigTidspunkt: Instant? = null
)