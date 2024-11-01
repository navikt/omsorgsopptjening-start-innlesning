package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.external.barnetrygd

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Ident
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class WiremockFagsak(val eier: Ident, val perioder: List<BarnetrygdPeriode>) {

    data class BarnetrygdPeriode(
        val personIdent: Ident,
        val delingsProsentYtelse: String = "FULL",
        val ytelsestype: String = "ORDINÆR_BARNETRYGD",
        val utbetaltPerMnd: Int = 2000,
        val stønadFom: YearMonth = YearMonth.of(2020, 1),
        val stønadTom: YearMonth = YearMonth.of(2025, 12),
    ) {
        fun toMap(): Map<String, Any?> {
            return mapOf(
                Pair("personIdent", personIdent.value),
                Pair("delingsProsentYtelse", delingsProsentYtelse),
                Pair("ytelsestype", ytelsestype),
                Pair("utbetaltPerMnd", utbetaltPerMnd),
                Pair("stønadFom", stønadFom.formatterForKall()),
                Pair("stønadTom", stønadTom.formatterForKall()),
            )
        }
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            Pair("eier", eier.value),
            Pair("perioder", perioder.map { it.toMap() }),
        )
    }

    companion object {
        fun YearMonth.formatterForKall() = this.format(yearMonthFormatter)!!
        val yearMonthFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
    }
}