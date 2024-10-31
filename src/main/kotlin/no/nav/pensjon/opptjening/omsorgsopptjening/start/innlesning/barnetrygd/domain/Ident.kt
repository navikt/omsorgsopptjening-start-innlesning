package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

data class Ident(val value: String) {
    init {
        assert(value.matches("^[0-9]+$".toRegex())) { "Ugyldig verdi for Ident: $value" }
    }

    override fun toString(): String {
        return "Ident($value)"
    }
}