package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Service

@Service
class StatusRapporteringsService (
    private val statusService: StatusService,
    private val registry: MeterRegistry,
) {
    companion object {
        private lateinit var statusMalere : MicrometerStatusMalere;
    }
    init {
        statusMalere = MicrometerStatusMalere(registry)
    }

    fun oppdaterRapporterbarStatus() {
        statusMalere.oppdater(statusService.checkStatus())
    }
}