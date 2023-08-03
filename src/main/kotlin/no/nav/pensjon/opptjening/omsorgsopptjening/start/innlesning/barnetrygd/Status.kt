package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.Instant
import java.time.temporal.ChronoUnit

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
sealed class Status {
    @JsonTypeName("Klar")
    data class Klar(val opprettet:Instant = Instant.now()): Status() {
        override fun ferdig(): Ferdig {
            return Status.Ferdig()
        }
        override fun retry(): Status {
           return Status.Retry()
        }
    }
    @JsonTypeName("Ferdig")
    data class Ferdig(val opprettet:Instant = Instant.now()): Status() {
    }
    @JsonTypeName("Retry")
    data class Retry(
        val opprettet: Instant = Instant.now(),
        val antallForsok: Int = 1,
        val maksAntallForsok: Int = 3,
        val nesteRetry: Instant = opprettet.plus(1, ChronoUnit.DAYS)
    ): Status() {

        override fun ferdig(): Ferdig {
           return Status.Ferdig()
        }
        override fun retry(): Status {
            return when{
                antallForsok +1  == maksAntallForsok -> Feilet()
                antallForsok +1 != maksAntallForsok -> copy(antallForsok = antallForsok+1)
                else -> throw IllegalArgumentException("Ugyldig statusoppdatering. Kan ikke gå fra $this til ${Retry::class.java}")}
        }
    }
    @JsonTypeName("Feilet")
    data class Feilet(val opprettet:Instant = Instant.now()): Status() {
    }
    open fun ferdig(): Status.Ferdig { throw IllegalArgumentException("Ugyldig statusoppdatering. Kan ikke gå fra $this til ${Ferdig::class.java}") }
    open fun retry(): Status { throw IllegalArgumentException("Ugyldig statusoppdatering. Kan ikke gå fra $this til ${Retry::class.java}") }



}


