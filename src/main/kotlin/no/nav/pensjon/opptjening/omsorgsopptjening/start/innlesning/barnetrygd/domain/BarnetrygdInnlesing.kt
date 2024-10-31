package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import java.time.Instant

sealed class BarnetrygdInnlesing {
    abstract val id: InnlesingId
    abstract val år: År
    abstract val forespurtTidspunkt: Instant
    abstract val forventetAntallIdentiteter: Long?

    open fun startet(forventetAntallIdenter: Long): Startet = throw UgyldigTilstand(
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
        override val år: År,
        override val forespurtTidspunkt: Instant,
    ) : BarnetrygdInnlesing() {
        override val forventetAntallIdentiteter: Long? = null
        override fun startet(forventetAntallIdenter: Long): Startet {
            return Startet(id, år, forespurtTidspunkt, Instant.now(), null, forventetAntallIdenter)
        }
    }

    data class Startet(
        override val id: InnlesingId,
        override val år: År,
        override val forespurtTidspunkt: Instant,
        val startTidspunkt: Instant,
        val antallIdenterLest: Int?,
        override val forventetAntallIdentiteter: Long? = null
    ) : BarnetrygdInnlesing() {

        override fun mottaData(): Startet {
            return this
        }

        override fun ferdig(): Ferdig {
            return Ferdig(
                id = id,
                år = år,
                forespurtTidspunkt = forespurtTidspunkt,
                startTidspunkt = startTidspunkt,
                ferdigTidspunkt = Instant.now(),
                antallIdenterLest = antallIdenterLest,
                forventetAntallIdentiteter = forventetAntallIdentiteter
            )
        }
    }

    data class Ferdig(
        override val id: InnlesingId,
        override val år: År,
        override val forespurtTidspunkt: Instant,
        val startTidspunkt: Instant,
        val ferdigTidspunkt: Instant,
        val antallIdenterLest: Int?,
        override val forventetAntallIdentiteter: Long?,
    ) : BarnetrygdInnlesing()

    companion object Factory {
        fun of(
            id: InnlesingId,
            år: År,
            forespurtTidspunkt: Instant,
            startTidspunkt: Instant?,
            ferdigTidspunkt: Instant?,
            antallIdenterLest: Int?,
            forventetAntallIdentiteter: Long?,
        ): BarnetrygdInnlesing {
            return if (ferdigTidspunkt != null && startTidspunkt != null && antallIdenterLest != null) {
                Ferdig(
                    id,
                    år,
                    forespurtTidspunkt,
                    startTidspunkt,
                    ferdigTidspunkt,
                    antallIdenterLest,
                    forventetAntallIdentiteter,
                )
            } else if (ferdigTidspunkt == null && startTidspunkt != null && antallIdenterLest != null) {
                Startet(id, år, forespurtTidspunkt, startTidspunkt, antallIdenterLest, forventetAntallIdentiteter)
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