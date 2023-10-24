package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.hjelpestønad.domain

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Kilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Omsorgstype
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.PersongrunnlagMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.hjelpestønad.external.HjelpestønadClient
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.hjelpestønad.external.HjelpestønadType
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.hjelpestønad.external.HjelpestønadVedtak
import org.springframework.stereotype.Component
import java.time.YearMonth

@Component
class HjelpestønadService(
    private val hjelpestønadClient: HjelpestønadClient
) {
    internal fun hentHjelpestønad(barnetrygdSak: PersongrunnlagMelding.Persongrunnlag): List<PersongrunnlagMelding.Omsorgsperiode> {
        return barnetrygdSak.omsorgsperioder
            .flatMap { barnetrygdperiode ->
                hentHjelpestønad(
                    fnr = barnetrygdperiode.omsorgsmottaker,
                    fom = barnetrygdperiode.fom,
                    tom = barnetrygdperiode.tom
                ).map { hjelpestønadPeriode ->
                    PersongrunnlagMelding.Omsorgsperiode(
                        fom = hjelpestønadPeriode.fom,
                        tom = hjelpestønadPeriode.tom,
                        omsorgstype = when (hjelpestønadPeriode.omsorgstype) {
                            HjelpestønadType.FORHØYET_SATS_3 -> Omsorgstype.HJELPESTØNAD_FORHØYET_SATS_3
                            HjelpestønadType.FORHØYET_SATS_4 -> Omsorgstype.HJELPESTØNAD_FORHØYET_SATS_4
                        },
                        omsorgsmottaker = hjelpestønadPeriode.ident,
                        kilde = Kilde.INFOTRYGD,
                        medlemskap = barnetrygdperiode.medlemskap,
                        utbetalt = barnetrygdperiode.utbetalt,
                        landstilknytning = barnetrygdperiode.landstilknytning,
                    )
                }
            }
    }

    private fun hentHjelpestønad(fnr: String, fom: YearMonth, tom: YearMonth): List<HjelpestønadVedtak> {
        return hjelpestønadClient.hentHjelpestønad(fnr = fnr, fom = fom.atDay(1), tom = tom.atEndOfMonth())
    }
}