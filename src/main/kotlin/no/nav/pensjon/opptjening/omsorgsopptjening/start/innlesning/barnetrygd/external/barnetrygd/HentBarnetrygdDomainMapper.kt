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
        return external.mapNotNull { map(it, filter) }
    }

    private fun map(
        external: BarnetrygdSak,
        filter: GyldigÅrsintervallFilter
    ): PersongrunnlagMelding.Persongrunnlag? {
        PersongrunnlagMelding.Persongrunnlag.of(
            omsorgsyter = external.fagsakEiersIdent,
            omsorgsperioder = external.barnetrygdPerioder
                .perioderIØnsketTidsrom(filter)
                .utenUtvidetBarnetrygd(external.fagsakEiersIdent)
                .utenSmåbarnstillegg()
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
                        omsorgstype = toDomainDelingsprosent(periode),
                        omsorgsmottaker = periode.personIdent,
                        kilde = periode.kildesystem.map(),
                        utbetalt = periode.utbetaltPerMnd,
                        landstilknytning = toDomainLandstilknytning(periode)
                    )
                },
            hjelpestønadsperioder = emptyList()
        )
    }

    private fun List<BarnetrygdPeriode>.perioderIØnsketTidsrom(filter: GyldigÅrsintervallFilter): List<BarnetrygdPeriode> {
        return filter {
            it.årsintervall()
                .let { invervall -> invervall.contains(filter.min().year) || invervall.contains(filter.max().year) }
        }
    }

    /**
     * Utvidet barnetrygd er et tillegg til ordinær barnetrygd som gis til omsorgsyter, tillegget i seg selv er ikke
     * relevant for å vurdere omsorgsopptjening. BA løsningen håndterer dissse periodene på samme måte som ordinære
     * barnetrygdperioder, men med ulik ytelsestype, samt en kobling mot omsorgsyter og ikke barnet.
     *
     * For perioder som hentes fra Infotrygd kan periodene med utvidet barnetrygd være koblet til omsorgsmottakeren,
     * i disse tilfellene er det implisitt at man også har ordinær barnetrygd, disse tilfellene må håndteres på lik linje
     * som ordinære barnnetrygdperioder fra BA.
     */
    private fun List<BarnetrygdPeriode>.utenUtvidetBarnetrygd(fagsagEiersIdent: String): List<BarnetrygdPeriode> {
        return filterNot { fagsagEiersIdent == it.personIdent && it.ytelseTypeEkstern == YtelseTypeEkstern.UTVIDET_BARNETRYGD }
    }

    private fun List<BarnetrygdPeriode>.utenSmåbarnstillegg(): List<BarnetrygdPeriode> {
        return filterNot { it.ytelseTypeEkstern == YtelseTypeEkstern.SMÅBARNSTILLEGG }
    }

    private fun toDomainDelingsprosent(periode: BarnetrygdPeriode): Omsorgstype {
        return when (periode.delingsprosentYtelse) {
            DelingsprosentYtelse.FULL -> Omsorgstype.FULL_BARNETRYGD
            DelingsprosentYtelse.DELT -> Omsorgstype.DELT_BARNETRYGD
            DelingsprosentYtelse.USIKKER -> Omsorgstype.USIKKER_BARNETRYGD
        }
    }

    private fun toDomainLandstilknytning(periode: BarnetrygdPeriode): Landstilknytning {
        return when {
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
    }

    fun BarnetrygdKilde.map(): Kilde {
        return when (this) {
            BarnetrygdKilde.BA -> Kilde.BARNETRYGD
            BarnetrygdKilde.Infotrygd -> Kilde.INFOTRYGD
        }
    }
}