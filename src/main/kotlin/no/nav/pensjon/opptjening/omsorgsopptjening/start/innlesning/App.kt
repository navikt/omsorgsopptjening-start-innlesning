package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning

import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.retry.annotation.EnableRetry


@SpringBootApplication
@EnableJwtTokenValidation
@EnableRetry
class App

fun main(args: Array<String>) {
    runApplication<App>(*args)
}