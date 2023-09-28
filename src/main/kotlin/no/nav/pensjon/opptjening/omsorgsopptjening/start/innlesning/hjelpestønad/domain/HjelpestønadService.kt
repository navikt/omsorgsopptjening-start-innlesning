package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.hjelpestønad.domain

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.OmsorgsgrunnlagMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.hjelpestønad.external.HjelpestønadClient
import org.springframework.stereotype.Component
import java.time.YearMonth

@Component
class HjelpestønadService(
    private val hjelpestønadClient: HjelpestønadClient
) {
    fun hentForOmsorgsmottakere(omsorgsmottakere: Map<String, Pair<YearMonth, YearMonth>>): List<OmsorgsgrunnlagMelding.VedtakPeriode> {
        return omsorgsmottakere
            .mapNotNull { (fnr, minMaxDate) ->
                hjelpestønadClient.hentHjelpestønad(
                    fnr = fnr,
                    fom = minMaxDate.first.atDay(1),
                    tom = minMaxDate.second.atEndOfMonth()
                )
            }
            .flatten()
    }
}