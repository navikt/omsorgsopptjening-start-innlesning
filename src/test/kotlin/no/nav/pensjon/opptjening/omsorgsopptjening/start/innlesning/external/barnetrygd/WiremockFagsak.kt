package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.barnetrygd

data class WiremockFagsak(val eier: String, val perioder: List<BarnetrygdPeriode>) {
    data class BarnetrygdPeriode(
        val personIdent: String,
        val delingsProsentYtelse : String = "FULL",
        val ytelsestype : String = "ORDINÆR BARNETRYGD",
        val utbetaltPerMnd: Int = 2000,
        val stonadFom: String = "2020-01",
        val stonadTom: String = "2025-12",
    )
}