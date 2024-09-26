package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

data class Person(
    val fnr: String,
    val historiske: Set<String>
) {
    fun identifisertAv(fnr: String): Boolean {
        return historiske.contains(fnr)
    }
}