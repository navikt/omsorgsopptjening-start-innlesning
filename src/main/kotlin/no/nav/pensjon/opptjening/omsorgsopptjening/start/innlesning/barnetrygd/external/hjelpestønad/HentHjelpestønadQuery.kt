package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.hjelpestønad

import java.time.LocalDate

data class HentHjelpestønadQuery(
    val fnr: String,
    val fom: LocalDate,
    val tom: LocalDate,
)