package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external

import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.deserialize
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.RådataFraKilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.OmsorgsgrunnlagMelding
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
                        HentBarnetrygdResponse.Feil(
                            status = response.statusCode.value(),
                            body = null
                        )
                    }

                    else -> {
                        deserialize<FagsakListeWrapper>(response.body!!).let { wrapper ->
                            when {
                                wrapper.fagsaker.isEmpty() -> {
                                    HentBarnetrygdResponse.Feil(
                                        status = status.value(),
                                        body = "Liste med barnetrygdsaker er tom"
                                    )
                                }

                                wrapper.fagsaker.any { it.barnetrygdPerioder.isEmpty() } -> {
                                    HentBarnetrygdResponse.Feil(
                                        status = status.value(),
                                        body = "En eller flere av barnetrygdsakene mangler perioder"
                                    )
                                }

                                else -> {
                                    HentBarnetrygdResponse.Ok(
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
                HentBarnetrygdResponse.Feil(
                    status = response.statusCode.value(),
                    body = response.body.toString()
                )
            }
        }
    }
}

sealed class HentBarnetrygdResponse {
    data class Ok(
        val barnetrygdsaker: List<OmsorgsgrunnlagMelding.Sak>,
        val rådataFraKilde: RådataFraKilde
    ) : HentBarnetrygdResponse()

    data class Feil(
        val status: Int?,
        val body: String?
    ) : HentBarnetrygdResponse()
}


private data class FagsakListeWrapper(
    val fagsaker: List<Sak>
)

data class Sak(
    val fagsakId: String,
    val fagsakEiersIdent: String,
    val barnetrygdPerioder: List<Periode>,
)

data class Periode(
    val personIdent: String,
    val delingsprosentYtelse: Int,
    val ytelseTypeEkstern: String?,
    val utbetaltPerMnd: Int,
    val stønadFom: YearMonth,
    val stønadTom: YearMonth
) {
    @JsonIgnore
    val periode: no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.periode.Periode =
        no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.periode.Periode(stønadFom, stønadTom)
}