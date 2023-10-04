package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.web

import com.google.gson.JsonObject
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.hystrix.MicrometerMetricsPublisher
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.mapToJson
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdmottakerService
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.StatusRapporteringsService
import no.nav.security.token.support.core.api.Protected
import no.nav.security.token.support.core.api.Unprotected
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
@Unprotected
class PrometheusWebApi(
    private val barnetrygdService: BarnetrygdmottakerService,
    private val registry: MeterRegistry
) {
    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    @GetMapping("/actuator/prometheus", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getPrometheusData(): ResponseEntity<String> {
        return ResponseEntity.ok(registry.mapToJson())
    }
}