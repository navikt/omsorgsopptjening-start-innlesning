package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

data class PersonId(
    val fnr: Ident,
    val historiske: Set<String>
)