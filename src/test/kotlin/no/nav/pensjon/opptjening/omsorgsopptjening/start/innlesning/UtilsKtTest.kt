package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Month
import java.time.YearMonth

class UtilsKtTest {
    @Test
    fun `lav verdi begrenses til nedre grense`() {
        assertThat(
            nedreGrense(
                måned = YearMonth.of(1800, Month.JANUARY),
                grense = YearMonth.of(1900, Month.JANUARY),
            )
        ).isEqualTo(YearMonth.of(1900, Month.JANUARY))
    }

    @Test
    fun `høy verdi begrenses ikke av nedre grense`() {
        assertThat(
            nedreGrense(
                måned = YearMonth.of(2100, Month.JANUARY),
                grense = YearMonth.of(1900, Month.JANUARY),
            )
        ).isEqualTo(YearMonth.of(2100, Month.JANUARY))
    }

    @Test
    fun `høy verdi begrenses til øvre grense`() {
        assertThat(
            øvreGrense(
                måned = YearMonth.of(2100, Month.JANUARY),
                grense = YearMonth.of(1900, Month.JANUARY),
            )
        ).isEqualTo(YearMonth.of(1900, Month.JANUARY))
    }

    @Test
    fun `lav verdi begrenses ikke av øvre grense`() {
        assertThat(
            øvreGrense(
                måned = YearMonth.of(1800, Month.JANUARY),
                grense = YearMonth.of(1900, Month.JANUARY),
            )
        ).isEqualTo(YearMonth.of(1800, Month.JANUARY))
    }
}