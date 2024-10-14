package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.RådataFraKilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.PersongrunnlagMelding

data class HentHjelpestønadResponse(
    val perioder: List<PersongrunnlagMelding.Hjelpestønadperiode>,
    val rådataFraKilde: RådataFraKilde,
)