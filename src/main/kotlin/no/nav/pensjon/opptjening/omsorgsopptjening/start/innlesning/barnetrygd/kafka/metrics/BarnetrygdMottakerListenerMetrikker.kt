package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.kafka.metrics

import io.micrometer.core.instrument.MeterRegistry
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.metrics.MetricsMåling
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.kafka.BarnetrygdmottakerKafkaMelding
import org.springframework.stereotype.Component

@Component
class BarnetrygdMottakerListenerMetrikker(registry: MeterRegistry) : MetricsMåling<BarnetrygdmottakerKafkaMelding> {

    private val antallLesteMeldinger = registry.counter("meldinger", "antall", "lest")
    private val barnetrygdMeldingStatusStart = registry.counter("barnetrygdMelding", "status", "start")
    private val barnetrygdMeldingStatusData = registry.counter("barnetrygdMelding", "status", "data")
    private val barnetrygdMeldingStatusSlutt = registry.counter("barnetrygdMelding", "status", "slutt")
    override fun mål(lambda: () -> BarnetrygdmottakerKafkaMelding): BarnetrygdmottakerKafkaMelding {
        val kafkamelding = lambda.invoke()

        antallLesteMeldinger.increment()
        when (kafkamelding.meldingstype) {
            BarnetrygdmottakerKafkaMelding.Type.START -> barnetrygdMeldingStatusStart.increment()
            BarnetrygdmottakerKafkaMelding.Type.DATA -> barnetrygdMeldingStatusData.increment()
            BarnetrygdmottakerKafkaMelding.Type.SLUTT -> barnetrygdMeldingStatusSlutt.increment()
        }
        return kafkamelding
    }
}

