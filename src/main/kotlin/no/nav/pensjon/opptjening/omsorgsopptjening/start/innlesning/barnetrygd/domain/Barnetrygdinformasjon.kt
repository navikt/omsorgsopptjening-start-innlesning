package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Rådata
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.PersongrunnlagMelding
import java.time.Instant
import java.util.*

data class Barnetrygdinformasjon(
    val id: UUID,
    val barnetrygdmottakerId: UUID,
    val created: Instant = Instant.now(),
    val ident: Ident,
    val persongrunnlag: List<PersongrunnlagMelding.Persongrunnlag>,
    val rådata: Rådata,
    val correlationId: CorrelationId,
    val innlesingId: InnlesingId,
    val status: Status,
) {
    enum class Status {
        KLAR,
        SENDT,
    }

    override fun equals(other: Any?): Boolean {
        return when (other) {
            is Barnetrygdinformasjon -> {
                Objects.equals(id, other.id)
                        && Objects.equals(barnetrygdmottakerId, other.barnetrygdmottakerId)
                        && Objects.equals(ident, other.ident)
                        && Objects.equals(persongrunnlag, other.persongrunnlag)
                        && Objects.equals(rådata, other.rådata)
                        && Objects.equals(correlationId, other.correlationId)
                        && Objects.equals(innlesingId, other.innlesingId)
                        && Objects.equals(status, other.status)
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
