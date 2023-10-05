package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.OmsorgsgrunnlagMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Omsorgstype

internal object HentBarnetrygdDomainMapper {

    fun map(external: List<BarnetrygdSak>): List<OmsorgsgrunnlagMelding.Sak> {
        return external.map { map(it) }
    }

    private fun map(external: BarnetrygdSak): OmsorgsgrunnlagMelding.Sak {
        return OmsorgsgrunnlagMelding.Sak(
            omsorgsyter = external.fagsakEiersIdent,
            vedtaksperioder = external.barnetrygdPerioder.map {
                OmsorgsgrunnlagMelding.VedtakPeriode(
                    fom = it.stønadFom,
                    tom = it.stønadTom,
                    omsorgstype = when (it.delingsprosentYtelse) {
                        DelingsprosentYtelse.FULL -> Omsorgstype.FULL_BARNETRYGD
                        DelingsprosentYtelse.DELT -> Omsorgstype.DELT_BARNETRYGD
                        DelingsprosentYtelse.USIKKER -> Omsorgstype.USIKKER_BARNETRYGD
                    },
                    omsorgsmottaker = it.personIdent
                )
            }
        )
    }
}