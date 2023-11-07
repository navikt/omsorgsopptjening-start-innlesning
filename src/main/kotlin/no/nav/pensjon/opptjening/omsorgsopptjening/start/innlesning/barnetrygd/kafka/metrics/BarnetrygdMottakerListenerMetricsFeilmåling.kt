package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.kafka.metrics

import io.micrometer.core.instrument.MeterRegistry
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.metrics.MetricsFeilmåling
import org.springframework.stereotype.Component

@Component
class BarnetrygdMottakerListenerMetricsFeilmåling(registry: MeterRegistry): MetricsFeilmåling<Unit> {

    private val antallFeiledeMeldinger = registry.counter("meldinger", "antall", "feilet")
    override fun målfeil(lambda: () -> Unit) {
        antallFeiledeMeldinger.increment()
    }
}
