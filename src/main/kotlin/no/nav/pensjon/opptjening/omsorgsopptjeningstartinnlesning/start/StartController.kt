package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.start

import no.nav.security.token.support.core.api.Protected
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
@Protected
class StartController {

    @GetMapping("/start/innlesning/{ar}")
    fun startInnlesning(@PathVariable ar: String): ResponseEntity<String> =
        ResponseEntity.ok("""Startet innlesning for $ar""")

}