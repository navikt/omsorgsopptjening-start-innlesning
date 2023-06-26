package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.start

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import java.time.LocalDate
import java.time.Month

@Component
class BarnetrygdClient(val webClient: WebClient, @Value("\${BARNETRYGD_URL}") val url: String) {

    fun initierSendingAvIdenter(ar: Int) =
        webClient
            .post()
            .uri("$url/api/ekstern/pensjon/hent-barnetrygdmottakere")
            .body(BodyInserters.fromValue(InitierBarnetrygdDetaljerRequest(LocalDate.of(ar, Month.JANUARY,1).toString())))
            .retrieve()
            .getBody()

    fun hentBarnetrygdDetaljer(ident: String, ar: Int) =
        webClient
            .post()
            .uri("$url/api/ekstern/pensjon/hent-barnetrygd")
            .body(BodyInserters.fromValue(BarnetrygdDetaljerRequest(ident, LocalDate.of(ar, Month.JANUARY,1).toString())))
            .retrieve()
            .getBody()
}

private data class BarnetrygdDetaljerRequest(val ident: String, val fraDato: String)
private data class InitierBarnetrygdDetaljerRequest(val fraDato: String)


fun WebClient.ResponseSpec.getBody() = bodyToMono(String::class.java).block()