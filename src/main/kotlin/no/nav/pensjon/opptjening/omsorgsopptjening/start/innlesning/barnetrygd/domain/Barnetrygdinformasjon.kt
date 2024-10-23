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
) {
    enum class Status {
        KLAR,
        SENDT,
    }

    override fun equals(other: Any?): Boolean {
        return when (val o = other) {
            is Barnetrygdinformasjon -> {
                Objects.equals(id, o.id)
                        && Objects.equals(barnetrygdmottakerId, o.barnetrygdmottakerId)
                        && Objects.equals(ident, o.ident)
                        && Objects.equals(persongrunnlag, o.persongrunnlag)
                        && Objects.equals(rådata, o.rådata)
                        && Objects.equals(correlationId, o.correlationId)
                        && Objects.equals(innlesingId, o.innlesingId)
                        && Objects.equals(status, o.status)
            }

            else -> false
        }
    }

    fun sendt(): Barnetrygdinformasjon {
        return copy(status = Status.SENDT)
    }

    override fun hashCode(): Int {
        return Objects.hash(
            id, barnetrygdmottakerId, ident, persongrunnlag, rådata, correlationId, innlesingId, status
        )
    }
}
