package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.web

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdInnlesing
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdInnlesingRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdmottakerService
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.BestillBarnetrygdmottakereResponse
import no.nav.security.token.support.core.api.Protected
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@Protected
class BarnetrygdWebApi(
    private val barnetrygdService: BarnetrygdmottakerService,
    private val innlesingRepository: BarnetrygdInnlesingRepository,
) {
    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    @GetMapping("/innlesning/start/{ar}")
    fun startInnlesning(@PathVariable ar: Int): ResponseEntity<String> {
        return barnetrygdService.bestillPersonerMedBarnetrygd(ar).let {
            when (it) {
                is BestillBarnetrygdmottakereResponse.Feil -> {
                    ResponseEntity.status(it.status ?: 500).body(it.melding ?: "Ukjent feil")
                }

                is BestillBarnetrygdmottakereResponse.Ok -> {
                    log.info("Bestilt innlesing: ${it.innlesingId} av barnetrygdmottakere for år:${it.år}")
                    innlesingRepository.bestilt(
                        BarnetrygdInnlesing.Bestilt(
                            id = it.innlesingId,
                            år = it.år,
                            forespurtTidspunkt = Instant.now(),
                        )
                    )
                    ResponseEntity.ok("""${it.innlesingId}""")
                }
            }
        }
    }
}