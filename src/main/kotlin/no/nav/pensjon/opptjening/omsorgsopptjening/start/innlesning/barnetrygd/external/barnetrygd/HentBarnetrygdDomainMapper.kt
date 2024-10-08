package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.barnetrygd

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Kilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Landstilknytning
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Omsorgstype
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.PersongrunnlagMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.GyldigÅrsintervallFilter
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.nedreGrense
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.øvreGrense

internal object HentBarnetrygdDomainMapper {

    fun map(
        external: List<BarnetrygdSak>,
        filter: GyldigÅrsintervallFilter
    ): List<PersongrunnlagMelding.Persongrunnlag> {
        return external.map { map(it, filter) }
    }

    private fun map(
        external: BarnetrygdSak,
        filter: GyldigÅrsintervallFilter
    ): PersongrunnlagMelding.Persongrunnlag {
        fun BarnetrygdKilde.map(): Kilde {
            return when (this) {
                BarnetrygdKilde.BA -> Kilde.BARNETRYGD
                BarnetrygdKilde.Infotrygd -> Kilde.INFOTRYGD
            }
        }

        return PersongrunnlagMelding.Persongrunnlag.of(
            omsorgsyter = external.fagsakEiersIdent,
            omsorgsperioder = external.barnetrygdPerioder
                .filter { periode ->
                    //fjern alle perioder som ikke har noen overlapp med filter
                    periode.årsintervall().let { it.contains(filter.min().year) || it.contains(filter.max().year) }
                }
                .map { periode ->
                    PersongrunnlagMelding.Omsorgsperiode(
                        fom = nedreGrense(
                            måned = periode.stønadFom,
                            grense = filter.min()
                        ),
                        tom = øvreGrense(
                            måned = periode.stønadTom,
                            grense = filter.max()
                        ),
                        omsorgstype = when (periode.delingsprosentYtelse) {
                            DelingsprosentYtelse.FULL -> Omsorgstype.FULL_BARNETRYGD
                            DelingsprosentYtelse.DELT -> Omsorgstype.DELT_BARNETRYGD
                            DelingsprosentYtelse.USIKKER -> Omsorgstype.USIKKER_BARNETRYGD
                        },
                        omsorgsmottaker = periode.personIdent,
                        kilde = periode.kildesystem.map(),
                        utbetalt = periode.utbetaltPerMnd,
                        landstilknytning = when {
                            periode.sakstypeEkstern == Sakstype.NASJONAL -> {
                                Landstilknytning.NORGE
                            }

                            periode.sakstypeEkstern == Sakstype.EØS && periode.norgeErSekundærlandMedNullUtbetaling == true -> {
                                require(periode.utbetaltPerMnd == 0) { "Forventet utbetaling lik 0 dersom norgeErSekundærlandMedNullUtbetaling er true" }
                                Landstilknytning.EØS_NORGE_SEKUNDÆR
                            }

                            periode.sakstypeEkstern == Sakstype.EØS && periode.norgeErSekundærlandMedNullUtbetaling == false -> {
                                Landstilknytning.EØS_UKJENT_PRIMÆR_OG_SEKUNDÆR_LAND
                            }

                            periode.sakstypeEkstern == Sakstype.EØS && periode.norgeErSekundærlandMedNullUtbetaling == null -> {
                                Landstilknytning.EØS_UKJENT_PRIMÆR_OG_SEKUNDÆR_LAND
                            }

                            else -> {
                                throw RuntimeException("Klarte ikke å oversette sakstypeEkstern: ${periode.sakstypeEkstern}")
                            }
                        }
                    )
                },
            hjelpestønadsperioder = emptyList()
        )
    }
}