package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.RådataFraKilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.IdentRolle
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.periode.Periode

sealed class BarnetrygdException(msg: String, cause: Throwable) : RuntimeException(msg, cause) {
    class FeilVedHentingAvPersonId(
        val fnr: Ident,
        val rolle: IdentRolle,
        msg: String,
        cause: Throwable,
        val rådata: List<RådataFraKilde>,
    ) : BarnetrygdException(msg, cause)

    class OverlappendePerioder(
        msg: String,
        cause: Throwable,
        val perioder: List<Periode>,
        val omsorgsmottaker: String,
        val rådata: List<RådataFraKilde>,
    ) : BarnetrygdException(msg, cause)

    class FeilIGrunnlagsdata(
        msg: String,
        cause: Throwable,
        val rådata: List<RådataFraKilde>,
    ) : BarnetrygdException(msg, cause)

}