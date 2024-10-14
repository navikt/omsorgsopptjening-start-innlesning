package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.RådataFraKilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.PersongrunnlagMelding

data class HentBarnetrygdResponse(
    val barnetrygdsaker: List<PersongrunnlagMelding.Persongrunnlag>,
    val rådataFraKilde: RådataFraKilde
)
