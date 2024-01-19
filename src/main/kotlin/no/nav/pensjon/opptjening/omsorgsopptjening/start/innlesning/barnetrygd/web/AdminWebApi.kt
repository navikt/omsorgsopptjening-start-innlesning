package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.web

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdmottakerService
import no.nav.security.token.support.core.api.Protected
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE
import org.springframework.http.MediaType.TEXT_PLAIN_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@Protected
class AdminWebApi(
    barnetrygdmottakerService: BarnetrygdmottakerService
) {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(AdminWebApi::class.java)
        private val secureLog: Logger = LoggerFactory.getLogger("secure")
    }

    @PostMapping("/start/stopp", consumes = [APPLICATION_FORM_URLENCODED_VALUE], produces = [TEXT_PLAIN_VALUE])
    fun stoppMeldinger(
        @RequestParam("uuidliste") meldingerString: String,
        @RequestParam("begrunnelse") begrunnelse: String? = null
    ): ResponseEntity<String> {
        return ResponseEntity.ok("Ikke implementert: stopp-flere")
    }

    @PostMapping("/start/avslutt", consumes = [APPLICATION_FORM_URLENCODED_VALUE], produces = [TEXT_PLAIN_VALUE])
    fun avslutt(
        @RequestParam("uuidliste") meldingerString: String,
        @RequestParam("begrunnelse") begrunnelse: String? = null
    ): ResponseEntity<String> {
        return ResponseEntity.ok("Ikke implementert: avslutt-flere")
    }

    fun parseUUIDListe(meldingerString: String): List<UUID> {
        return meldingerString.lines()
            .map { it.replace("[^0-9a-f-]".toRegex(), "") }
            .filter { it.isNotEmpty() }
            .map { UUID.fromString(it.trim()) }
    }
}