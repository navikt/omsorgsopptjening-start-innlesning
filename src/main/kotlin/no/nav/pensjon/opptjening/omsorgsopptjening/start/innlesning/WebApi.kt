package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.BarnetrygdClientResponse
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.BarnetrygdProcessingTask
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
//@Protected
class WebApi(
    private val innlesningService: InnlesningService,
) {

    @GetMapping("/innlesning/start/{ar}")
    fun startInnlesning(@PathVariable ar: Int): ResponseEntity<String> {
        return innlesningService.initierSendingAvIdenter(ar).let {
            when (it) {
                is BarnetrygdClientResponse.Feil -> {
                    ResponseEntity.status(it.status ?: 500).body(it.body ?: "Ukjent feil")
                }

                is BarnetrygdClientResponse.Ok -> {
                    ResponseEntity.ok("""Startet innlesning for $ar""")
                }
            }
        }
    }

    @GetMapping("/test")
    fun test(): ResponseEntity<String> {
        return try {
            innlesningService.prosesserBarnetrygdmottakere()
            ResponseEntity.ok().body("ok")
        } catch (ex: Throwable) {
            BarnetrygdProcessingTask.log.error("Exception caught while processing, message:${ex.message}, cause:${ex.cause}")
            ResponseEntity.internalServerError().body(ex.message)
        }
    }
}