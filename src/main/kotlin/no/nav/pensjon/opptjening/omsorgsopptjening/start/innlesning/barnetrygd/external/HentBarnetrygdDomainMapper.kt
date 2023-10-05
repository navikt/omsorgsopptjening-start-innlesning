package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Kilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Omsorgstype
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.PersongrunnlagMelding

internal object HentBarnetrygdDomainMapper {

    fun map(external: List<BarnetrygdSak>): List<PersongrunnlagMelding.Persongrunnlag> {
        return external.map { map(it) }
    }

    private fun map(external: BarnetrygdSak): PersongrunnlagMelding.Persongrunnlag {
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
                    kilde = when (it.kilde) {
                        BarnetrygdKilde.BA -> Kilde.BARNETRYGD
                        BarnetrygdKilde.INFOTRYGD -> Kilde.INFOTRYGD
                    }
                )
            }
        )
    }
}