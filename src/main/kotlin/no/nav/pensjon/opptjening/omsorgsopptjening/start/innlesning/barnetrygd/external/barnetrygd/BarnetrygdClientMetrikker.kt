package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.barnetrygd

import io.micrometer.core.instrument.MeterRegistry
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.metrics.MetricsMåling
import org.springframework.stereotype.Component

@Component
class BarnetrygdClientMetrikker(registry: MeterRegistry) : MetricsMåling<HentBarnetrygdResponse?> {

    private val timer = registry.timer("hentbarnetrygd", "tidsbruk", "hentet")

    override fun mål(lambda: () -> HentBarnetrygdResponse?): HentBarnetrygdResponse? {
        return timer.recordCallable(lambda)!!
    }
}

