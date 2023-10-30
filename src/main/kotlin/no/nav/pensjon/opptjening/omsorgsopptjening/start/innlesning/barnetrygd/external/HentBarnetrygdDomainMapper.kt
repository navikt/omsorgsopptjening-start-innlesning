package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Kilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Landstilknytning
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Omsorgstype
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.PersongrunnlagMelding

internal object HentBarnetrygdDomainMapper {

    fun map(external: List<BarnetrygdSak>): List<PersongrunnlagMelding.Persongrunnlag> {
        return external.map { map(it) }
    }

    private fun map(external: BarnetrygdSak): PersongrunnlagMelding.Persongrunnlag {
        fun BarnetrygdKilde.map(): Kilde {
            return when (this) {
                BarnetrygdKilde.BA -> Kilde.BARNETRYGD
                BarnetrygdKilde.INFOTRYGD -> Kilde.INFOTRYGD
            }
        }

        return PersongrunnlagMelding.Persongrunnlag(
            omsorgsyter = external.fagsakEiersIdent,
            omsorgsperioder = external.barnetrygdPerioder.map {
                PersongrunnlagMelding.Omsorgsperiode(
                    fom = it.stønadFom,
                    tom = it.stønadTom,
                    omsorgstype = when (it.delingsprosentYtelse) {
                        DelingsprosentYtelse.FULL -> Omsorgstype.FULL_BARNETRYGD
                        DelingsprosentYtelse.DELT -> Omsorgstype.DELT_BARNETRYGD
                        DelingsprosentYtelse.USIKKER -> Omsorgstype.USIKKER_BARNETRYGD
                    },
                    omsorgsmottaker = it.personIdent,
                    kilde = it.kildesystem.map(),
                    utbetalt = it.utbetaltPerMnd,
                    landstilknytning = when {
                        it.sakstypeEkstern == Sakstype.NASJONAL -> {
                            Landstilknytning.NORGE
                        }

                        it.sakstypeEkstern == Sakstype.EØS && it.norgeErSekundærlandMedNullUtbetaling == true -> {
                            require(it.utbetaltPerMnd == 0) { "Forventet utbetaling lik 0 dersom norgeErSekundærlandMedNullUtbetaling er true" }
                            Landstilknytning.EØS_NORGE_SEKUNDÆR
                        }

                        it.sakstypeEkstern == Sakstype.EØS && it.norgeErSekundærlandMedNullUtbetaling == false -> {
                            Landstilknytning.EØS_UKJENT_PRIMÆR_OG_SEKUNDÆR_LAND
                        }

                        it.sakstypeEkstern == Sakstype.EØS && it.norgeErSekundærlandMedNullUtbetaling == null -> {
                            Landstilknytning.EØS_UKJENT_PRIMÆR_OG_SEKUNDÆR_LAND
                        }

                        else -> {
                            throw RuntimeException("Klarte ikke å oversette sakstypeEkstern: ${it.sakstypeEkstern}")
                        }
                    }
                )
            }
        )
    }
}