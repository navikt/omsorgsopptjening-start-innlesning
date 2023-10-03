package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.mapToJson
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
        assertThat(målere.antallOk()).isOne()
        assertThat(målere.antallFeil()).isZero()
        assertThat(målere.antallMangler()).isZero()
        assertThat(målere.feilmelding()).isEmpty()
    }

    @Test
    fun testFeil() {
        val målere = MicrometerStatusMalere(registry)
        målere.oppdater(ApplicationStatus.Feil("Dette er en feil"))
        assertThat(målere.antallOk()).isZero()
        assertThat(målere.antallFeil()).isOne()
        assertThat(målere.antallMangler()).isZero()
        assertThat(målere.feilmelding()).isEqualTo("Dette er en feil")
    }

    @Test
    fun testManglerIkkeKjort() {
        val målere = MicrometerStatusMalere(registry)
        målere.oppdater(ApplicationStatus.IkkeKjort)
        assertThat(målere.antallOk()).isZero()
        assertThat(målere.antallFeil()).isZero()
        assertThat(målere.antallMangler()).isOne()
        assertThat(målere.feilmelding()).isEmpty()
    }

    @Test
    fun testManglerIkkeAvgitt() {
        val målere = MicrometerStatusMalere(registry)
        assertThat(målere.antallOk()).isZero()
        assertThat(målere.antallFeil()).isZero()
        assertThat(målere.antallMangler()).isOne()
        assertThat(målere.feilmelding()).isEmpty()
    }

    @Test
    fun testMikrometerIkkeMerEnnEn() {
        val målere = MicrometerStatusMalere(registry)
        målere.oppdater(ApplicationStatus.OK)
        målere.oppdater(ApplicationStatus.OK)
        målere.oppdater(ApplicationStatus.OK)
        val okCount = registry.get("applikasjonsstatus.ok").gauge().value().toInt()
        assertThat(okCount).isOne()
    }

    @Test
    fun testMikrometerAndreTilbakeTilNull() {
        val målere = MicrometerStatusMalere(registry)
        målere.oppdater(ApplicationStatus.OK)
        målere.oppdater(ApplicationStatus.IkkeKjort)
        val okCount = registry.get("applikasjonsstatus.ok").gauge().value().toInt()
        assertThat(okCount).isZero()
        val manglerCount = registry.get("applikasjonsstatus.ukjent").gauge().value().toInt()
        println("Mangler: $manglerCount")
        assertThat(manglerCount).isOne()
    }


}