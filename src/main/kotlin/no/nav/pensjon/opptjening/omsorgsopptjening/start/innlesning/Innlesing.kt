package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.KafkaMelding
import java.time.Instant

data class Innlesing(
    val id: InnlesingId,
    val år: Int,
    val forespurtTidspunkt: Instant? = null,
    val startTidspunkt: Instant? = null,
    val ferdigTidspunkt: Instant? = null
) {
    fun kanStartes() = erBestilt() && !erStartet() && !erFerdig()

    fun kanMottaData() = erStartet() && !erFerdig()

    fun kanAvsluttes() = kanMottaData()
    private fun erBestilt() = forespurtTidspunkt != null
    private fun erStartet() = startTidspunkt != null
    private fun erFerdig() = ferdigTidspunkt != null
}

sealed class InnlesingException : RuntimeException() {
    data class EksistererIkke(val id: String) : RuntimeException()
    data class UgyldigTistand(val kafkaMelding: KafkaMelding.Type, val innlesing: Innlesing) :
        RuntimeException("Ugyldig tilstand for innlesing: $innlesing og kafkamelding: $kafkaMelding")
}