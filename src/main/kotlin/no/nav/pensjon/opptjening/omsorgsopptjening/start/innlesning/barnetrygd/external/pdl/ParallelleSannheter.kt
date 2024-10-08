package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.pdl

private const val FOLKEREGISTERET = "FREG"

private infix fun Metadata.harMaster(antattMaster: String) = master.uppercase() == antattMaster
