package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.feilinfo

import java.time.Instant

data class Feilinfo(val time: Instant, val data: String) {
    fun shortened(): Feilinfo {
        return if (data.length > 65400) {
            Feilinfo(time, data.substring(65400))
        } else {
            this
        }
    }
}