package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.deserialize
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.RådataFraKilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.PersongrunnlagMelding
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.time.YearMonth

object HentBarnetrygdResponseHandler {
    fun handle(response: ResponseEntity<String>): HentBarnetrygdResponse {
        /**
         * Finnes barna til personen det spørres på i flere fagsaker vil det være flere elementer i listen
         * Ett element pr. fagsak barnet er knyttet til.
         * Kan være andre personer enn mor og far.
         */
        return when (val status = response.statusCode) {
            HttpStatus.OK -> {
                when {
                    response.body == null -> {
                        throw HentBarnetrygdException("Liste med barnetrygdsaker er null")
                    }

                    else -> {
                        deserialize<FagsakListeWrapper>(response.body!!).let { wrapper ->
                            when {
                                wrapper.fagsaker.isEmpty() -> {
                                    throw HentBarnetrygdException("Liste med barnetrygdsaker er tom")
                                }

                                wrapper.fagsaker.any { it.barnetrygdPerioder.isEmpty() } -> {
                                    throw HentBarnetrygdException("En eller flere av barnetrygdsakene mangler perioder")
                                }

                                else -> {
                                    HentBarnetrygdResponse(
                                        barnetrygdsaker = HentBarnetrygdDomainMapper.map(wrapper.fagsaker),
                                        rådataFraKilde = RådataFraKilde(response.body!!)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                throw HentBarnetrygdException("Ukjent feil, status: $status, body:${response.body.toString()}")
            }
        }
    }
}

data class HentBarnetrygdResponse(
    val barnetrygdsaker: List<PersongrunnlagMelding.Persongrunnlag>,
    val rådataFraKilde: RådataFraKilde
)

data class HentBarnetrygdException(val msg: String) : RuntimeException(msg)


private data class FagsakListeWrapper(
    val fagsaker: List<BarnetrygdSak>
)

internal data class BarnetrygdSak(
    val fagsakEiersIdent: String,
    val barnetrygdPerioder: List<BarnetrygdPeriode>,
)

internal data class BarnetrygdPeriode(
    val personIdent: String,
    val delingsprosentYtelse: DelingsprosentYtelse,
    val ytelseTypeEkstern: String?,
    val utbetaltPerMnd: Int,
    val stønadFom: YearMonth,
    val stønadTom: YearMonth,
    val sakstypeEkstern: Sakstype,
    val kildesystem: BarnetrygdKilde,
    val pensjonstrygdet: Boolean? = null,
    val norgeErSekundærlandMedNullUtbetaling: Boolean? = null
)

internal enum class DelingsprosentYtelse {
    FULL, //full barnetrygd
    DELT, //delt barnetrygd
    USIKKER //ikke nok data til å avgjøre, kan være tilfelle på gamle saker fra Infotrygd
}

internal enum class BarnetrygdKilde {
    BA, //barnetrygdsystemet
    Infotrygd
}

internal enum class Sakstype {
    NASJONAL,
    EØS
}