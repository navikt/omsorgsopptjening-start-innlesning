package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.barnetrygd

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.År
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

object BestillBarnetrygdResponseHandler {
    fun handle(
        response: ResponseEntity<String>,
        år: År,
    ): BestillBarnetrygdmottakereResponse {
        return when (response.statusCode) {
            HttpStatus.ACCEPTED -> {
                BestillBarnetrygdmottakereResponse(
                    innlesingId = InnlesingId.fromString(response.body.toString()),
                    år = år
                )
            }

            else -> {
                throw BestillBarnetrygdMottakereException("Ukjent feil, status: ${response.statusCode.value()}, body: ${response.body.toString()}")
            }
        }
    }
}

data class BestillBarnetrygdmottakereResponse(
    val innlesingId: InnlesingId,
    val år: År,
)

data class BestillBarnetrygdMottakereException(val msg: String) : RuntimeException(msg)