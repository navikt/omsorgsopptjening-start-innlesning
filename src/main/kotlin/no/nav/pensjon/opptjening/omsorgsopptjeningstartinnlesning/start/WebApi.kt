package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.start

import no.nav.security.token.support.core.api.Protected
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
@Protected
class WebApi(private val innlesningService: InnlesningService) {

    @GetMapping("/innlesning/start/{ar}")
    fun startInnlesning(@PathVariable ar: Int): ResponseEntity<String> {
        innlesningService.initierSendingAvIdenter(ar)
        return ResponseEntity.ok("""Startet innlesning for $ar""")
    }
}