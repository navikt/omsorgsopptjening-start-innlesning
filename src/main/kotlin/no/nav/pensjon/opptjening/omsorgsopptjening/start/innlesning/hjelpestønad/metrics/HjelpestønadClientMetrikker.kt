package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.hjelpestønad.metrics

import io.micrometer.core.instrument.MeterRegistry
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.hjelpestønad.external.HentHjelpestønadResponse
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.metrics.MetricsMåling
import org.springframework.stereotype.Component

@Component
class HjelpestønadClientMetrikker(registry: MeterRegistry) : MetricsMåling<HentHjelpestønadResponse?> {

    private val timer = registry.timer("henthjelpestonad", "tidsbruk", "hentet")

    override fun mål(lambda: () -> HentHjelpestønadResponse?): HentHjelpestønadResponse? {
        return timer.recordCallable(lambda)!!
    }
}

