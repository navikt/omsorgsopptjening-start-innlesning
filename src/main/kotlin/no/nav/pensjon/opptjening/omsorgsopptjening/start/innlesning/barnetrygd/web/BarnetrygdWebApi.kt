package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.web

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdmottakerService
import no.nav.security.token.support.core.api.Protected
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
@Protected
class BarnetrygdWebApi(
    private val barnetrygdService: BarnetrygdmottakerService,
) {
    companion object {
        val log: Logger = LoggerFactory.getLogger(BarnetrygdWebApi::class.java)
    }

    @GetMapping("/innlesning/start/{ar}")
    fun startInnlesning(@PathVariable ar: Int): ResponseEntity<String> {
        return barnetrygdService.bestillPersonerMedBarnetrygd(ar).let {
            log.info("Bestilt innlesing: ${it.id} av barnetrygdmottakere for år:${it.år}")
            ResponseEntity.ok("""${it.id}""")
        }
    }
}