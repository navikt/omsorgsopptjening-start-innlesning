package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.start

import no.nav.security.token.support.core.api.Protected
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Protected
class StartController {

    @GetMapping("/start")
    fun start() {

    }
}