package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.metrics

import io.micrometer.core.instrument.MeterRegistry
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Barnetrygdmottaker
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.metrics.MetricsMåling
import org.springframework.stereotype.Component

@Component
class BarnetrygdmottakerProcessingMetrikker(registry: MeterRegistry) : MetricsMåling<List<Barnetrygdmottaker>?> {

    private val antallKlar = registry.counter("barnetrygdmottaker", "status", "klar")
    private val antallFerdig = registry.counter("barnetrygdmottaker", "status", "ferdig")
    private val antallRetry = registry.counter("barnetrygdmottaker", "status", "retry")
    private val antallFeilet = registry.counter("barnetrygdmottaker", "status", "feilet")

    // TODO: avsluttet og stoppet er nye statuser, og admin-grensesnittet må oppdateres til å ta høyde for de
    private val antallAvsluttet = registry.counter("barnetrygdmottaker", "status", "avsluttet")
    private val antallStoppet = registry.counter("barnetrygdmottaker", "status", "stoppet")
    private val timer = registry.timer("barnetrygdmottaker", "tidsbruk", "prosessert")

    override fun mål(lambda: () -> List<Barnetrygdmottaker>?): List<Barnetrygdmottaker>? {
        return timer.recordCallable(lambda)?.onEach {
            when (it.status) {
                is Barnetrygdmottaker.Status.Feilet -> antallFeilet.increment()
                is Barnetrygdmottaker.Status.Ferdig -> antallFerdig.increment()
                is Barnetrygdmottaker.Status.Klar -> antallKlar.increment()
                is Barnetrygdmottaker.Status.Retry -> antallRetry.increment()
                is Barnetrygdmottaker.Status.Avsluttet -> antallAvsluttet.increment()
                is Barnetrygdmottaker.Status.Stoppet -> antallStoppet.increment()
            }
        }
    }
}

