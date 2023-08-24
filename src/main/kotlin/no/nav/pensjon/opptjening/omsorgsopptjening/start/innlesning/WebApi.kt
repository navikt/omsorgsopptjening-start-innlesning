package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.BarnetrygdService
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.HentBarnetygdmottakereResponse
import no.nav.security.token.support.core.api.Protected
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
@Protected
class WebApi(
    private val barnetrygdService: BarnetrygdService,
    private val innlesingRepo: InnlesingRepo,
) {
    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    @GetMapping("/innlesning/start/{ar}")
    fun startInnlesning(@PathVariable ar: Int): ResponseEntity<String> {
        return barnetrygdService.initierSendingAvIdenter(ar).let {
            when (it) {
                is HentBarnetygdmottakereResponse.Feil -> {
                    ResponseEntity.status(it.status ?: 500).body(it.body ?: "Ukjent feil")
                }

                is HentBarnetygdmottakereResponse.Ok -> {
                    innlesingRepo.forespurt(Innlesing(id = it.requestId, år = it.år))
                    ResponseEntity.ok("""Forespurt innlesning: ${it.requestId} for $ar""")
                }
            }
        }
    }
}