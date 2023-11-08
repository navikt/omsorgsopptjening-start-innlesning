package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.kafka.metrics

import io.micrometer.core.instrument.ImmutableTag
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.metrics.MetricsMåling
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.kafka.BarnetrygdmottakerKafkaMelding
import org.springframework.stereotype.Component

@Component
class BarnetrygdMottakerListenerMetrikker(private val registry: MeterRegistry) : MetricsMåling<BarnetrygdmottakerKafkaMelding> {

    override fun mål(lambda: () -> BarnetrygdmottakerKafkaMelding): BarnetrygdmottakerKafkaMelding {
        val kafkamelding = lambda.invoke()

        val antallLesteMeldinger = registry.counter("meldinger", "antall", "lest", "requestId", kafkamelding.requestId.toString())
        registry.gauge("antallMeldingerTotalt", listOf<Tag>(ImmutableTag("requestId",kafkamelding.requestId.toString())), kafkamelding.antallIdenterTotalt)

        antallLesteMeldinger.increment()

        when (kafkamelding.meldingstype) {
            BarnetrygdmottakerKafkaMelding.Type.START -> {
                val barnetrygdMeldingStatusStart = registry.counter("barnetrygdMelding", "status", "start", "requestId", kafkamelding.requestId.toString())
                barnetrygdMeldingStatusStart.increment()
            }
            BarnetrygdmottakerKafkaMelding.Type.DATA -> {
                val barnetrygdMeldingStatusData = registry.counter("barnetrygdMelding", "status", "data", "requestId", kafkamelding.requestId.toString())
                barnetrygdMeldingStatusData.increment()
            }
            BarnetrygdmottakerKafkaMelding.Type.SLUTT -> {
                val barnetrygdMeldingStatusSlutt = registry.counter("barnetrygdMelding", "status", "slutt", "requestId", kafkamelding.requestId.toString())
                barnetrygdMeldingStatusSlutt.increment()
            }
        }
        return kafkamelding
    }
}

