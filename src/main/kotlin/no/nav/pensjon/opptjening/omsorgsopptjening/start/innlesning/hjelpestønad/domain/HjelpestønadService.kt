package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.hjelpestønad.domain

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.RådataFraKilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Kilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Omsorgstype
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.PersongrunnlagMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.periode.Periode
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.GyldigÅrsintervallFilter
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.hjelpestønad.external.HentHjelpestønadResponse
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.hjelpestønad.external.HjelpestønadClient
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.hjelpestønad.external.HjelpestønadType
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.nedreGrense
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.øvreGrense
import org.springframework.stereotype.Component
import java.time.YearMonth

@Component
class HjelpestønadService(
    private val hjelpestønadClient: HjelpestønadClient
) {
    internal fun hentHjelpestønad(
//        persongrunnlag: PersongrunnlagMelding.Persongrunnlag,
        omsorgsmottakere: Set<String>,
        filter: GyldigÅrsintervallFilter,
    ): List<Pair<List<PersongrunnlagMelding.Hjelpestønadperiode>, RådataFraKilde>> {
        return omsorgsmottakere.map { omsorgsmottaker ->
            hentHjelpestønad(
                fnr = omsorgsmottaker,
                fom = filter.min(),
                tom = filter.max()
            ).let { response ->
                response.vedtak.mapNotNull { vedtak ->
                    val fom = nedreGrense(
                        måned = vedtak.fom,
                        grense = filter.min()
                    )
                    val tom = øvreGrense(
                        måned = vedtak.tom,
                        grense = filter.max()
                    )

                    val tomPeriode = Periode(fom, tom).antallMoneder() == 0

                    when (tomPeriode) {
                        true -> {
                            null
                        }

                        false -> {
                            PersongrunnlagMelding.Hjelpestønadperiode(
                                fom = nedreGrense(
                                    måned = vedtak.fom,
                                    grense = filter.min()
                                ),
                                tom = øvreGrense(
                                    måned = vedtak.tom,
                                    grense = filter.max()
                                ),
                                omsorgstype = when (vedtak.omsorgstype) {
                                    HjelpestønadType.FORHØYET_SATS_3 -> Omsorgstype.HJELPESTØNAD_FORHØYET_SATS_3
                                    HjelpestønadType.FORHØYET_SATS_4 -> Omsorgstype.HJELPESTØNAD_FORHØYET_SATS_4
                                },
                                omsorgsmottaker = vedtak.ident,
                                kilde = Kilde.INFOTRYGD,
                            )
                        }
                    }
                } to response.rådataFraKilde
            }
        }
    }

    private fun hentHjelpestønad(fnr: String, fom: YearMonth, tom: YearMonth): HentHjelpestønadResponse {
        return hjelpestønadClient.hentHjelpestønad(fnr = fnr, fom = fom.atDay(1), tom = tom.atEndOfMonth())
    }
}