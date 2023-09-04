package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import java.time.Instant

sealed class BarnetrygdInnlesing {
    abstract val id: InnlesingId
    abstract val år: Int
    abstract val forespurtTidspunkt: Instant
    open val startTidspunkt: Instant? = null
    open val ferdigTidspunkt: Instant? = null

    open fun startet(): Startet = throw UgyldigTilstand(
        fra = this::class.java.simpleName,
        til = Startet::class.java.simpleName
    )

    open fun mottaData(): Startet = throw UgyldigTilstand(
        fra = this::class.java.simpleName,
        til = Startet::class.java.simpleName
    )

    open fun ferdig(): Ferdig = throw UgyldigTilstand(
        fra = this::class.java.simpleName,
        til = Ferdig::class.java.simpleName
    )

    data class Bestilt(
        override val id: InnlesingId,
        override val år: Int,
        override val forespurtTidspunkt: Instant
    ) : BarnetrygdInnlesing() {
        override fun startet(): Startet {
            return Startet(id, år, forespurtTidspunkt, Instant.now())
        }
    }

    data class Startet(
        override val id: InnlesingId,
        override val år: Int,
        override val forespurtTidspunkt: Instant,
        override val startTidspunkt: Instant,
    ) : BarnetrygdInnlesing() {

        override fun mottaData(): Startet {
            return this
        }

        override fun ferdig(): Ferdig {
            return Ferdig(id, år, forespurtTidspunkt, startTidspunkt, Instant.now())
        }
    }

    data class Ferdig(
        override val id: InnlesingId,
        override val år: Int,
        override val forespurtTidspunkt: Instant,
        override val startTidspunkt: Instant,
        override val ferdigTidspunkt: Instant
    ) : BarnetrygdInnlesing()

    companion object Factory {
        fun of(
            id: InnlesingId,
            år: Int,
            forespurtTidspunkt: Instant,
            startTidspunkt: Instant?,
            ferdigTidspunkt: Instant?
        ): BarnetrygdInnlesing {
            return if (ferdigTidspunkt != null && startTidspunkt != null) {
                Ferdig(id, år, forespurtTidspunkt, startTidspunkt, ferdigTidspunkt)
            } else if (ferdigTidspunkt == null && startTidspunkt != null) {
                Startet(id, år, forespurtTidspunkt, startTidspunkt)
            } else {
                Bestilt(id, år, forespurtTidspunkt)
            }
        }
    }

    data class UgyldigTilstand(
        val fra: String,
        val til: String
    ) : RuntimeException()
}