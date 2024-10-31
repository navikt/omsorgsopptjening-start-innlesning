package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

data class År(val value: Int) {
    init {
        assert(value in 1901..2199) { "Ugyldig år: $value" }
    }

    override fun toString(): String {
        return "År($value)"
    }
}