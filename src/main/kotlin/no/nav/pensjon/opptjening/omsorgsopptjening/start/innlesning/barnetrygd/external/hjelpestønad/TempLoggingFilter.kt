package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.hjelpestønad

import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import reactor.core.publisher.Mono

fun logRequest(): ExchangeFilterFunction {
    return ExchangeFilterFunction.ofRequestProcessor { clientRequest ->
        clientRequest.body().let { body ->
            println("Request: ${clientRequest.method()} ${clientRequest.url()}")
            println("Request Headers: ${clientRequest.headers()}")
            println("Request Body: $body")
        }
        Mono.just(clientRequest)
    }
}