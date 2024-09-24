package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.person.external.pdl

private const val FOLKEREGISTERET = "FREG"

private infix fun Metadata.harMaster(antattMaster: String) = master.uppercase() == antattMaster
