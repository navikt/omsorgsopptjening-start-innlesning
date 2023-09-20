package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.OmsorgsgrunnlagMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Omsorgstype

object HentBarnetrygdDomainMapper {

    fun map(external: List<Sak>): List<OmsorgsgrunnlagMelding.Sak> {
        return external.map { map(it) }
    }

    fun map(external: Sak): OmsorgsgrunnlagMelding.Sak {
        return OmsorgsgrunnlagMelding.Sak(
            omsorgsyter = external.fagsakEiersIdent,
            vedtaksperioder = external.barnetrygdPerioder.map {
                OmsorgsgrunnlagMelding.VedtakPeriode(
                    fom = it.stønadFom,
                    tom = it.stønadTom,
                    omsorgstype = when (it.delingsprosentYtelse) {
                        50 -> Omsorgstype.DELT_BARNETRYGD
                        100 -> Omsorgstype.FULL_BARNETRYGD
                        else -> throw RuntimeException("Ukjent ytelseprosent: ${it.delingsprosentYtelse}")
                    },
                    omsorgsmottaker = it.personIdent
                )
            }
        )
    }
}