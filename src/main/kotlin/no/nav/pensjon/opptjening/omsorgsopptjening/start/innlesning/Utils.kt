package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning

import java.time.YearMonth


fun nedreGrense(måned: YearMonth, grense: YearMonth): YearMonth {
    return maxOf(måned, grense)
}

fun øvreGrense(måned: YearMonth?, grense: YearMonth): YearMonth {
    return måned?.let { minOf(måned, grense) } ?: grense
}