package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.hjelpestønad

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Kilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.OmsorgsgrunnlagMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Omsorgstype
import java.time.Month
import java.time.YearMonth
import java.util.UUID

class HjelpestønadRepo {
    fun leggTilEventuellHjelpestønad(saker: List<OmsorgsgrunnlagMelding.Sak>): List<OmsorgsgrunnlagMelding.Sak> {
        return saker
            .map { it to it.hentOmsorgsmottakere() }
            .map { (sak, omsorgsmottakere) -> sak to omsorgsmottakere.mapNotNull { finnHjelpestønadz(it) }.flatten() }
            .flatMap { (sak, hjelpestønadvedtak) ->
                hjelpestønadvedtak.map {
                    sak.leggTilVedtaksperiode(
                        OmsorgsgrunnlagMelding.VedtakPeriode(
                            fom = it.fom,
                            tom = it.tom,
                            omsorgstype = it.omsorgstype,
                            omsorgsmottaker = it.ident,
                        )
                    )
                }
            }
    }
}

fun finnHjelpestønadz(ident: String): List<HjelpestønadVedtak>? {
    """select * from tabell where barnfnr = $ident"""
    return listOf(
        HjelpestønadVedtak(
            id = UUID.randomUUID(),
            ident = "1234568910",
            fom = YearMonth.of(2020, Month.JANUARY),
            tom = YearMonth.of(2030, Month.JANUARY),
            omsorgstype = Omsorgstype.HJELPESTØNAD_FORHØYET_SATS_3,
            kilde = Kilde.INFOTRYGD
        )
    )
}
