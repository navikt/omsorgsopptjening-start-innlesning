package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID


data class Barnetrygdmottaker(
    val id: UUID? = null,
    var opprettet: Instant? = null,
    var ident: String,
    var år: Int? = null,
    var correlationId: CorrelationId,
    val statushistorikk: List<Status> = listOf(Status.Klar()),
    val innlesingId: InnlesingId
) {
    constructor(
        ident: String,
        correlationId: CorrelationId,
        innlesingId: InnlesingId
    ) : this(
        id = null,
        opprettet = null,
        år = null,
        ident = ident,
        correlationId = correlationId,
        innlesingId = innlesingId
    )

    val status: Status get() = statushistorikk.last()
    fun ferdig(): Barnetrygdmottaker {
        return copy(statushistorikk = statushistorikk + status.ferdig())
    }

    fun retry(melding: String): Barnetrygdmottaker {
        return copy(statushistorikk = statushistorikk + status.retry(melding))
    }

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
    )
    sealed class Status {

        open fun ferdig(): Ferdig {
            throw IllegalArgumentException("Kan ikke gå fra status:${this::class.java} til Ferdig")
        }

        open fun retry(melding: String): Status {
            throw IllegalArgumentException("Kan ikke gå fra status:${this::class.java} til Retry")
        }

        @JsonTypeName("Klar")
        data class Klar(
            val tidspunkt: Instant = Instant.now()
        ) : Status() {
            override fun ferdig(): Ferdig {
                return Ferdig()
            }

            override fun retry(melding: String): Status {
                return Retry(melding = melding)
            }
        }

        @JsonTypeName("Ferdig")
        data class Ferdig(
            val tidspunkt: Instant = Instant.now(),
        ) : Status()

        @JsonTypeName("Retry")
        data class Retry(
            val tidspunkt: Instant = Instant.now(),
            val antallForsøk: Int = 1,
            val maxAntallForsøk: Int = 3,
            val karanteneTil: Instant = tidspunkt.plus(5, ChronoUnit.HOURS),
            val melding: String,
        ) : Status() {
            override fun ferdig(): Ferdig {
                return Ferdig()
            }

            override fun retry(melding: String): Status {
                return when {
                    antallForsøk < maxAntallForsøk -> {
                        Retry(
                            tidspunkt = Instant.now(),
                            antallForsøk = antallForsøk + 1,
                            melding = melding,
                        )
                    }

                    antallForsøk == maxAntallForsøk -> {
                        Feilet()
                    }

                    else -> {
                        super.retry(melding)
                    }
                }
            }
        }

        @JsonTypeName("Feilet")
        data class Feilet(
            val tidspunkt: Instant = Instant.now(),
        ) : Status()
    }

}