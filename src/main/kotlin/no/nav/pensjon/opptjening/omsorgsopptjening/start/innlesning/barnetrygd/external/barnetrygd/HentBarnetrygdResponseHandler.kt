package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.barnetrygd

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.deserialize
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.UgyldigPersongrunnlag
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Rådata
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.RådataFraKilde
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdException
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.GyldigÅrsintervallFilter
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.HentBarnetrygdResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.time.YearMonth

object HentBarnetrygdResponseHandler {
    fun handle(
        request: BarnetrygdClient.HentBarnetrygdRequest,
        response: ResponseEntity<String>,
        filter: GyldigÅrsintervallFilter
    ): HentBarnetrygdResponse {
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
                                wrapper.fagsaker == null -> {
                                    throw HentBarnetrygdException("Liste med barnetrygdsaker mangler")
                                }

                                else -> {
                                    val rådata = RådataFraKilde(
                                        mapOf(
                                            "fnr" to request.ident,
                                            "fom" to request.fraDato,
                                            "barnetrygd" to "${response.body}"
                                        )
                                    )
                                    try {
                                        HentBarnetrygdResponse(
                                            barnetrygdsaker = HentBarnetrygdDomainMapper.map(wrapper.fagsaker, filter),
                                            rådataFraKilde = rådata
                                        )
                                    } catch (e: UgyldigPersongrunnlag.OverlappendeOmsorgsperiode) {
                                        throw BarnetrygdException.OverlappendePerioder(
                                            msg = "Feil ved deserialisering av barnetrygdsaker",
                                            cause = e,
                                            perioder = e.perioder,
                                            rådata = Rådata(listOf(rådata))
                                        )
                                    } catch (e: UgyldigPersongrunnlag) {
                                        throw BarnetrygdException.FeilIGrunnlagsdata(
                                            msg = "Feil ved deserialisering av barnetrygdsaker",
                                            cause = e,
                                            rådata = Rådata(listOf(rådata))
                                        )
                                    }
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

data class HentBarnetrygdException(val msg: String) : RuntimeException(msg)


private data class FagsakListeWrapper(
    val fagsaker: List<BarnetrygdSak>?
)

internal data class BarnetrygdSak(
    val fagsakEiersIdent: String,
    val barnetrygdPerioder: List<BarnetrygdPeriode>,
)

internal data class BarnetrygdPeriode(
    val personIdent: String,
    val delingsprosentYtelse: DelingsprosentYtelse,
    val ytelseTypeEkstern: YtelseTypeEkstern,
    val utbetaltPerMnd: Int,
    val stønadFom: YearMonth,
    val stønadTom: YearMonth?,
    val sakstypeEkstern: Sakstype,
    val kildesystem: BarnetrygdKilde,
    val pensjonstrygdet: Boolean? = null,
    val norgeErSekundærlandMedNullUtbetaling: Boolean? = null
) {
    fun årsintervall(): IntRange = stønadFom.year..(stønadTom?.year ?: Int.MAX_VALUE)
}

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

internal enum class YtelseTypeEkstern {
    ORDINÆR_BARNETRYGD,
    UTVIDET_BARNETRYGD,
    SMÅBARNSTILLEGG,
}