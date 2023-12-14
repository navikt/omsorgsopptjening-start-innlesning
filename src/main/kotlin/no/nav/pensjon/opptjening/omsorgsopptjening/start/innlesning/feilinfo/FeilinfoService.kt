package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.feilinfo

import org.springframework.stereotype.Component
import java.time.Clock

@Component
class FeilinfoService(
    private val feilinfoRepository: FeilinfoRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun lagre(feilinfo: Feilinfo) {
        feilinfoRepository.lagre(feilinfo.shortened())
    }

    fun lagre(data: String) {
        val feilinfo = Feilinfo(clock.instant(), data)
        lagre(feilinfo)
    }
}