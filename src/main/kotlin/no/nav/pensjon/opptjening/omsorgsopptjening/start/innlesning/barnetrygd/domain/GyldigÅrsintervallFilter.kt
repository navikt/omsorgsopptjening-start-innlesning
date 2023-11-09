package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import java.time.LocalDate
import java.time.Month
import java.time.YearMonth

class GyldigÅrsintervallFilter(
    år: Int,
) {
    private val gyldigIntervall: IntRange = år..år + 1

    fun intervall(): IntRange = gyldigIntervall
    fun minDato(): LocalDate = min().atDay(1)
    fun maxDato(): LocalDate = min().atEndOfMonth()
    fun min(): YearMonth = YearMonth.of(gyldigIntervall.min(), Month.JANUARY)
    fun max(): YearMonth = YearMonth.of(gyldigIntervall.max(), Month.DECEMBER)
}