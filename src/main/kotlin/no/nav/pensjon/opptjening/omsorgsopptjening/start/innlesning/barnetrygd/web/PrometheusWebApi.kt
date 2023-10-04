package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.web

import io.micrometer.core.instrument.MeterRegistry
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.mapToJson
import no.nav.security.token.support.core.api.Unprotected
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Unprotected
class PrometheusWebApi(
    private val registry: MeterRegistry
) {
    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    @GetMapping("/actuator/prometheus", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getPrometheusData(): ResponseEntity<String> {
        log.info("Prometheus hentet data")
        return ResponseEntity.ok(registry.mapToJson())
    }
}