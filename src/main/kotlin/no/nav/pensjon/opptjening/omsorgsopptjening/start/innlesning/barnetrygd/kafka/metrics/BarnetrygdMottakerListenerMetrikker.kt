package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.kafka.metrics

import io.micrometer.core.instrument.MeterRegistry
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.kafka.BarnetrygdmottakerKafkaMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.metrics.MetricsMåling
import org.springframework.stereotype.Component

@Component
class BarnetrygdMottakerListenerMetrikker(private val registry: MeterRegistry) :
    MetricsMåling<List<BarnetrygdmottakerKafkaMelding>> {

    override fun mål(lambda: () -> List<BarnetrygdmottakerKafkaMelding>): List<BarnetrygdmottakerKafkaMelding> {
        return lambda().also { meldinger ->
            meldinger.forEach {
                registry.counter("meldinger", "antall", "lest", "innlesingId", it.requestId.toString()).increment()
                when (it.meldingstype) {
                    BarnetrygdmottakerKafkaMelding.Type.START -> {
                        val barnetrygdMeldingStatusStart = registry.counter(
                            "barnetrygdMelding",
                            "status",
                            "start",
                            "innlesingId",
                            it.requestId.toString()
                        )
                        barnetrygdMeldingStatusStart.increment()
                    }

                    BarnetrygdmottakerKafkaMelding.Type.DATA -> {
                        val barnetrygdMeldingStatusData = registry.counter(
                            "barnetrygdMelding",
                            "status",
                            "data",
                            "innlesingId",
                            it.requestId.toString()
                        )
                        barnetrygdMeldingStatusData.increment()
                    }

                    BarnetrygdmottakerKafkaMelding.Type.SLUTT -> {
                        val barnetrygdMeldingStatusSlutt = registry.counter(
                            "barnetrygdMelding",
                            "status",
                            "slutt",
                            "innlesingId",
                            it.requestId.toString()
                        )
                        barnetrygdMeldingStatusSlutt.increment()
                    }
                }

            }
        }
    }
}

