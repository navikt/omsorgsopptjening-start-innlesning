package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.monitorering

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MicrometerStatusMalereTest {

    private lateinit var registry: MeterRegistry

    @BeforeEach
    fun setUp() {
        registry = SimpleMeterRegistry()
        Metrics.globalRegistry.add(registry)
    }

    @AfterEach
    fun tearDown() {
        registry.clear()
        Metrics.globalRegistry.clear()
    }

    @Test
    fun testOK() {
        val målere = MicrometerStatusMalere(registry)
        målere.oppdater(ApplicationStatus.OK)
        assertThat(målere.antallOk()).isEqualTo(9)
        assertThat(målere.antallFeil()).isZero()
        assertThat(målere.antallMangler()).isZero()
        assertThat(målere.feilmelding()).isEmpty()
        assertThat(målere.statusTekst()).isEqualTo("OK")
    }

    @Test
    fun testFeil() {
        val målere = MicrometerStatusMalere(registry)
        målere.oppdater(ApplicationStatus.Feil("Dette er en feil"))
        assertThat(målere.antallOk()).isZero()
        assertThat(målere.antallFeil()).isEqualTo(10)
        assertThat(målere.antallMangler()).isZero()
        assertThat(målere.feilmelding()).isEqualTo("Dette er en feil")
        assertThat(målere.statusTekst()).isEqualTo("Feil")
        assertThat(målere.statusKode()).isEqualTo(3)
    }

    @Test
    fun testManglerIkkeKjort() {
        val målere = MicrometerStatusMalere(registry)
        målere.oppdater(ApplicationStatus.IkkeKjort)
        assertThat(målere.antallOk()).isZero()
        assertThat(målere.antallFeil()).isZero()
        assertThat(målere.antallMangler()).isOne()
        assertThat(målere.feilmelding()).isEmpty()
        assertThat(målere.statusTekst()).isEqualTo("Mangler data")
        assertThat(målere.statusKode()).isEqualTo(2)
    }

    @Test
    fun testManglerIkkeAvgitt() {
        val målere = MicrometerStatusMalere(registry)
        assertThat(målere.antallOk()).isZero()
        assertThat(målere.antallFeil()).isZero()
        assertThat(målere.antallMangler()).isOne()
        assertThat(målere.feilmelding()).isEmpty()
        assertThat(målere.statusTekst()).isEqualTo("Mangler status")
        assertThat(målere.statusKode()).isEqualTo(0)
    }

    @Test
    fun testMikrometerØkerIkke() {
        val målere = MicrometerStatusMalere(registry)
        målere.oppdater(ApplicationStatus.OK)
        målere.oppdater(ApplicationStatus.OK)
        målere.oppdater(ApplicationStatus.OK)
        val okCount = registry.get("pensjonopptjening_applikasjonsstatus_ok").gauge().value().toInt()
        assertThat(okCount).isEqualTo(9)
        assertThat(målere.statusKode()).isEqualTo(1)
    }

    @Test
    fun testMikrometerAndreTilbakeTilNull() {
        val målere = MicrometerStatusMalere(registry)
        målere.oppdater(ApplicationStatus.OK)
        målere.oppdater(ApplicationStatus.IkkeKjort)
        val okCount = registry.get("pensjonopptjening_applikasjonsstatus_ok").gauge().value().toInt()
        assertThat(okCount).isZero()
        val manglerCount = registry.get("pensjonopptjening_applikasjonsstatus_ukjent").gauge().value().toInt()
        assertThat(manglerCount).isOne()
    }
}