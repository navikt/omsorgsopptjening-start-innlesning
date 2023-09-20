package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.hjelpestønad

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Kilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.OmsorgsgrunnlagMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Omsorgstype
import org.springframework.stereotype.Component
import java.time.Month
import java.time.YearMonth
import java.util.UUID

@Component
class HjelpestønadRepo {
    fun hentHjelpestønad(fnr: String): List<HjelpestønadVedtak>? {
        //TODO hjelpestønad sql + fnr
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
}