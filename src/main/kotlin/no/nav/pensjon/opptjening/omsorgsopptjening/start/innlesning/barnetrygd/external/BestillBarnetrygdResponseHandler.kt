package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

object BestillBarnetrygdResponseHandler {
    fun handle(response: ResponseEntity<String>, år: Int): BestillBarnetrygdmottakereResponse {
        return when (response.statusCode) {
            HttpStatus.ACCEPTED -> {
                BestillBarnetrygdmottakereResponse.Ok(
                    innlesingId = InnlesingId.fromString(response.body.toString()),
                    år = år
                )
            }

            else -> {
                BestillBarnetrygdmottakereResponse.Feil(
                    status = response.statusCode.value(),
                    melding = response.body.toString()
                )
            }
        }
    }
}

sealed class BestillBarnetrygdmottakereResponse {
    data class Ok(val innlesingId: InnlesingId, val år: Int) : BestillBarnetrygdmottakereResponse()
    data class Feil(val status: Int?, val melding: String?) : BestillBarnetrygdmottakereResponse()
}