package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.hjelpestønad

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.OmsorgsgrunnlagMelding
import org.springframework.stereotype.Component

@Component
class HjelpestønadService(
    private val hjelpestønadRepo: HjelpestønadRepo
) {
    fun hentForOmsorgsmottakere(omsorgsmottakere: Set<String>): List<OmsorgsgrunnlagMelding.VedtakPeriode> {
        return omsorgsmottakere
            .mapNotNull { hjelpestønadRepo.hentHjelpestønad(it) }
            .flatten()
            .map {
                OmsorgsgrunnlagMelding.VedtakPeriode(
                    fom = it.fom,
                    tom = it.tom,
                    omsorgstype = it.omsorgstype,
                    omsorgsmottaker = it.ident,
                )
            }
    }
}