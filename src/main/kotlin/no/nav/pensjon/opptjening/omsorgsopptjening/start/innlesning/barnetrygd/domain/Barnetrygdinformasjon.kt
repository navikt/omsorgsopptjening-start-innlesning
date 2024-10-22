package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Rådata
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.PersongrunnlagMelding
import java.time.Instant
import java.util.*

data class Barnetrygdinformasjon(
    val id: UUID,
    val barnetrygdmottakerId: UUID,
    val created: Instant = Instant.now(),
    val ident: String,
    val persongrunnlag: List<PersongrunnlagMelding.Persongrunnlag>,
    val rådata: Rådata,
    val correlationId: UUID,
    val innlesingId: UUID,
    val status: Status,
    val lockId: UUID? = null,
    val lockTime: Instant? = null,
) {
    enum class Status {
        KLAR,
        SENDT,
    }
}
