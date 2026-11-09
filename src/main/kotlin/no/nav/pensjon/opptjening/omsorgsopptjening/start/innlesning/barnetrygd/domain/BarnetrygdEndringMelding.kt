package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import java.time.LocalDate
import java.time.LocalDateTime

data class BarnetrygdEndringMelding(
    val ident: String,
    val vedtakstidspunkt: LocalDateTime,
    val endringstidspunkt: LocalDate,
    val sekvensnummer: Long,
)