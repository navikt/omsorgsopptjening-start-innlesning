package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.monitorering

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory

class MicrometerStatusMalere(private val registry: MeterRegistry) {

    private val log = LoggerFactory.getLogger(this::class.java)
    private var status : ApplicationStatus? = null

    init {
        Gauge
            .builder("pensjonopptjening_applikasjonsstatus_ok") { antallOk() }
            .tag("status","ok")
            .register(registry)
        Gauge
            .builder("pensjonopptjening_applikasjonsstatus_feil") { antallFeil() }
            .tag("status","feil")
            .tag("feilmelding",feilmelding())
            .register(registry)
        Gauge
            .builder("pensjonopptjening_applikasjonsstatus_ukjent") { antallMangler() }
            .tag("status","ukjent")
            .register(registry)
        Gauge
            .builder("pensjonopptjening_applikasjonsstatus_kode") { statusKode() }
            .tag("status", statusTekst())
            .register(registry)
    }

    fun oppdater(status: ApplicationStatus) {
        this.status = status
    }

    fun antallOk() : Int {
        return if (status == ApplicationStatus.OK) 9 else 0
    }

    fun antallFeil() : Int {
        return if (status is ApplicationStatus.Feil) 10 else 0
    }

    fun statusTekst() : String {
        return when (val status = status) {
            is ApplicationStatus.OK -> "OK"
            is ApplicationStatus.IkkeKjort -> "Mangler data"
            is ApplicationStatus.Feil -> "Feil"
            null -> "Mangler status"
        }
    }

    fun statusKode() : Int {
        return when (val status = status) {
            null -> 0
            is ApplicationStatus.OK -> 1
            is ApplicationStatus.IkkeKjort -> 2
            is ApplicationStatus.Feil -> 3
        }
    }

    fun antallMangler() : Int {
        val mangler = (status == null || status is ApplicationStatus.IkkeKjort)
        return if (mangler) 1 else 0
    }

    fun feilmelding() : String {
        val status = this.status
        return if (status is ApplicationStatus.Feil) status.feil else ""
    }

}