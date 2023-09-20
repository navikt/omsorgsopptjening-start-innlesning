package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.hjelpestønad

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Kilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Omsorgstype
import java.time.YearMonth
import java.util.UUID

data class HjelpestønadVedtak(
    val id: UUID? = null,
    val ident: String,
    val fom: YearMonth,
    val tom: YearMonth,
    val omsorgstype: Omsorgstype,
    val kilde: Kilde
)