package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning

import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication


@SpringBootApplication
@EnableJwtTokenValidation
class App

fun main(args: Array<String>) {
    runApplication<App>(*args)
}