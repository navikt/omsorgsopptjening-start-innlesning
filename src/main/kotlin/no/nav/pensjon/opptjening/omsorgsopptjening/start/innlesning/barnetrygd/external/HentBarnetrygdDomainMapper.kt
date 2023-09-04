package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.OmsorgsgrunnlagMelding

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
                    prosent = it.delingsprosentYtelse,
                    omsorgsmottaker = it.personIdent
                )
            }
        )
    }
}