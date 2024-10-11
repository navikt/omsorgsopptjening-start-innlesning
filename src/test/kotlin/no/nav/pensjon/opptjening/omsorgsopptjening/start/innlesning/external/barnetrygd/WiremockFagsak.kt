package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.barnetrygd

data class WiremockFagsak(val eier: String, val perioder: List<BarnetrygdPeriode>) {
    data class BarnetrygdPeriode(
        val personIdent: String,
        val delingsProsentYtelse: String = "FULL",
        val ytelsestype: String = "ORDINÆR_BARNETRYGD",
        val utbetaltPerMnd: Int = 2000,
        val stønadFom: String = "2020-01",
        val stønadTom: String = "2025-12",
    ) {
        fun toMap(): Map<String, Any?> {
            return mapOf(
                Pair("personIdent", personIdent),
                Pair("delingsProsentYtelse", delingsProsentYtelse),
                Pair("ytelsestype", ytelsestype),
                Pair("utbetaltPerMnd", utbetaltPerMnd),
                Pair("stønadFom", stønadFom),
                Pair("stønadTom", stønadTom),
            )
        }
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            Pair("eier", eier),
            Pair("perioder", perioder.map { it.toMap() }),
        )
    }
}