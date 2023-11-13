package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.metrics

import io.micrometer.core.instrument.MeterRegistry
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Barnetrygdmottaker
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.metrics.MetricsMåling
import org.springframework.stereotype.Component

@Component
class BarnetrygdmottakerProcessingMetrikker(private val registry: MeterRegistry) : MetricsMåling<Barnetrygdmottaker?> {

    val antallKlar = registry.counter("barnetrygdmottaker", "status", "klar")
    val antallFerdig = registry.counter("barnetrygdmottaker", "status", "ferdig")
    val antallRetry = registry.counter("barnetrygdmottaker", "status", "retry")
    val antallFeilet = registry.counter("barnetrygdmottaker", "status", "feilet")

    override fun mål(lambda: () -> Barnetrygdmottaker?): Barnetrygdmottaker? {
        val barnetrygmottaker = lambda.invoke()
        barnetrygmottaker?.statushistorikk?.map { it.kortStatus }?.distinct()?.forEach {
            when (it) {
                Barnetrygdmottaker.KortStatus.KLAR -> antallKlar.increment()
                Barnetrygdmottaker.KortStatus.FERDIG -> antallFerdig.increment()
                Barnetrygdmottaker.KortStatus.RETRY -> antallRetry.increment()
                Barnetrygdmottaker.KortStatus.FEILET -> antallFeilet.increment()
            }
        }
        return barnetrygmottaker
    }
}

