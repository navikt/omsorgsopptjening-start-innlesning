package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

data class BarnetrygdEndring(
    val id: Long?,
    val ident: Personidentifikator,
    /**
     * Tidspunktet vedtaket/endringen ble utført
     */
    val vedtakstidspunkt: LocalDateTime,
    /**
     * Den tidligste datoen endringen gjelder fra. I praksis tilsvarer dette fraOgMed dato for den tidligste
     * [no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.barnetrygd.BarnetrygdPeriode]
     * som er berørt av endringen.
     */
    val endringstidspunkt: LocalDate,
    val sekvensnummer: Long,
    val opprettet: Instant?,
)