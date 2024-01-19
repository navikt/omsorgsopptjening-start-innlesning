package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID


sealed class Barnetrygdmottaker {
    abstract val id: UUID?
    abstract val opprettet: Instant?
    abstract val ident: String
    abstract val correlationId: CorrelationId
    abstract val statushistorikk: List<Status>
    abstract val innlesingId: InnlesingId
    abstract val status: Status

    data class Transient(
        override val ident: String,
        override val correlationId: CorrelationId,
        override val innlesingId: InnlesingId,
    ) : Barnetrygdmottaker() {
        override val id = null
        override val opprettet = null
        override val statushistorikk: List<Status> = listOf(Status.Klar())
        override val status: Status get() = statushistorikk.last()
    }

    data class Mottatt(
        override val id: UUID,
        override val opprettet: Instant,
        override val ident: String,
        override val correlationId: CorrelationId,
        override val innlesingId: InnlesingId,
        override val statushistorikk: List<Status> = listOf(Status.Klar()),
        val år: Int
    ) : Barnetrygdmottaker() {
        override val status: Status get() = statushistorikk.last()

        fun ferdig(): Mottatt {
            return copy(statushistorikk = statushistorikk + status.ferdig())
        }

        fun retry(melding: String): Mottatt {
            return copy(statushistorikk = statushistorikk + status.retry(melding))
        }

        fun avsluttet(melding: String): Mottatt {
            return copy(statushistorikk = statushistorikk + status.avsluttet(melding))
        }

        fun stoppet(melding: String): Mottatt {
            return copy(statushistorikk = statushistorikk + status.avsluttet(melding))
        }

        fun klar(): Mottatt {
            return if (status is Status.Klar) {
                this
            } else {
                copy(statushistorikk = statushistorikk + status.klar())
            }
        }
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

        open fun avsluttet(melding: String): Status {
            throw IllegalArgumentException("Kan ikke gå fra status:${this::class.java} til Avsluttet")
        }

        open fun klar(): Status {
            throw IllegalArgumentException("Kan ikke gå fra status:${this::class.java} til Klar")
        }

        open fun stoppet(): Status {
            throw IllegalArgumentException("Kan ikke gå fra status:${this::class.java} til Stoppet")
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

        @JsonTypeName("Avsluttet")
        data class Avsluttet(
            val tidspunkt: Instant = Instant.now(),
            val begrunnelse: String,
        ) {
        }

        @JsonTypeName("Stoppet")
        data class Stoppet(
            val tidspunkt: Instant = Instant.now(),
            val begrunnelse: String,
        ) {
        }

        @JsonTypeName("Feilet")
        data class Feilet(
            val tidspunkt: Instant = Instant.now(),
        ) : Status()
    }
}