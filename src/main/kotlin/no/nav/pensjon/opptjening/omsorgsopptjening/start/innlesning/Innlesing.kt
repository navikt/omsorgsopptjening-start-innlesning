package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning

import java.time.Instant

data class Innlesing(
    val id: String,
    val år: Int,
    val forespurtTidspunkt: Instant? = null,
    val startTidspunkt: Instant? = null,
    val ferdigTidspunkt: Instant? = null
)