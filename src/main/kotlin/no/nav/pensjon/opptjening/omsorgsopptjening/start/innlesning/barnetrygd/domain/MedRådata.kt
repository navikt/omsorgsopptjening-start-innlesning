package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.RådataFraKilde

data class MedRådata<T>(
    val value: T,
    val rådata: List<RådataFraKilde>,
)