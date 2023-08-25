package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.periode.Periode
import java.time.YearMonth

/*
 * Finnes barna til personen det spørres på i flere fagsaker vil det være flere elementer i listen
 * Ett element pr. fagsak barnet er knyttet til.
 * Kan være andre personer enn mor og far.
 */
data class Barnetrygdmelding(
    val ident: String,
    val list: List<Sak>
){
    data class Sak(
        val fagsakId: String,
        val fagsakEiersIdent: String,
        val barnetrygdPerioder: List<Periode>,
    )

    data class Periode(
        val personIdent: String,
        val delingsprosentYtelse: Int,
        val utbetaltPerMnd: Int,
        val stønadFom: YearMonth,
        val stønadTom: YearMonth
    ){
        @JsonIgnore
        val periode: no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.periode.Periode = Periode(stønadFom, stønadTom)
    }
}