package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

sealed class BarnetrygdException(msg: String, cause: Throwable) : RuntimeException(msg, cause) {
    class FeilVedHentingAvPersonId(val fnr: String, msg: String, cause: Throwable) : BarnetrygdException(msg, cause)
    class OverlappendePerioder(msg: String, cause: Throwable): BarnetrygdException(msg, cause)
}