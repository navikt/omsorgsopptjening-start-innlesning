package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.web

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdmottakerService
import no.nav.security.token.support.core.api.Protected
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE
import org.springframework.http.MediaType.TEXT_PLAIN_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@Protected
class AdminWebApi(
    private val barnetrygdmottakerService: BarnetrygdmottakerService
) {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(AdminWebApi::class.java)
        private val secureLog: Logger = LoggerFactory.getLogger("secure")
    }

    @PostMapping("/start/stopp", consumes = [APPLICATION_FORM_URLENCODED_VALUE], produces = [TEXT_PLAIN_VALUE])
    fun stoppMeldinger(
        @RequestParam("uuidliste") uuidString: String,
        @RequestParam("begrunnelse") begrunnelse: String? = null
    ): ResponseEntity<String> {
        log.info("admin: stopper meldinger")
        val uuids = try {
            parseUUIDListe(uuidString)
        } catch (ex: Throwable) {
            return ResponseEntity.badRequest().body("Kunne ikke parse uuid'ene")
        }

        if (uuids.isEmpty()) {
            return ResponseEntity.badRequest().body("Ingen uuid'er oppgitt")
        }

        try {
            val responsStrenger =
                uuids.map { id ->
                    try {
                        val resultat = barnetrygdmottakerService.stopp(id, begrunnelse ?: "")
                        resultat.toString()
                    } catch (ex: Throwable) {
                        secureLog.info("Fikk exception under kansellering av oppgave", ex)
                        "$id: Feilet, ${ex::class.simpleName}"
                    }
                }
            val respons = responsStrenger.joinToString("\n")
            return ResponseEntity.ok(respons)
        } catch (ex: Throwable) {
            secureLog.info("admin:stopp feilet", ex)
            return ResponseEntity.internalServerError()
                .contentType(MediaType.TEXT_PLAIN)
                .body("Feil ved prosessering: $ex")
        }
    }

    @PostMapping("/start/avslutt", consumes = [APPLICATION_FORM_URLENCODED_VALUE], produces = [TEXT_PLAIN_VALUE])
    fun avslutt(
        @RequestParam("uuidliste") uuidString: String,
        @RequestParam("begrunnelse") begrunnelse: String? = null
    ): ResponseEntity<String> {
        val uuids = try {
            parseUUIDListe(uuidString)
        } catch (ex: Throwable) {
            return ResponseEntity.badRequest().body("Kunne ikke parse uuid'ene")
        }

        if (uuids.isEmpty()) {
            return ResponseEntity.badRequest().body("Ingen uuid'er oppgitt")
        }

        try {
            val responsStrenger =
                uuids.map { id ->
                    try {
                        val resultat = barnetrygdmottakerService.avslutt(id, begrunnelse ?: "")
                        resultat.toString()
                    } catch (ex: Throwable) {
                        secureLog.info("Fikk exception under kansellering av oppgave", ex)
                        "$id: Feilet, ${ex::class.simpleName}"
                    }
                }
            val respons = responsStrenger.joinToString("\n")
            return ResponseEntity.ok(respons)
        } catch (ex: Throwable) {
            return ResponseEntity.internalServerError()
                .contentType(MediaType.TEXT_PLAIN)
                .body("Feil ved prosessering: $ex")
        }
    }

    fun parseUUIDListe(meldingerString: String): List<UUID> {
        return meldingerString.lines()
            .map { it.replace("[^0-9a-f-]".toRegex(), "") }
            .filter { it.isNotEmpty() }
            .map { UUID.fromString(it.trim()) }
    }

    @GetMapping("/start/ping")
    fun ping(): ResponseEntity<String> {
        return ResponseEntity.ok("pong")
    }
}