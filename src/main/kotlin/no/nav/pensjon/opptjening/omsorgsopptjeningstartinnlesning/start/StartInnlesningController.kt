package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.start

import no.nav.security.token.support.core.api.Protected
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
@Protected
class StartInnlesningController(private val startInnlesningService: StartInnlesningService) {

    @GetMapping("/innlesning/start/{ar}")
    fun startInnlesning(@PathVariable ar: String): ResponseEntity<String> {
        startInnlesningService.startInnlesning(ar)
        return ResponseEntity.ok("""Startet innlesning for $ar""")
    }

    @GetMapping("/innlesning/historikk")
    fun startInnlesningHistorikk(): List<StartHistorikk> = startInnlesningService.startInnlesningHistorikk()
}