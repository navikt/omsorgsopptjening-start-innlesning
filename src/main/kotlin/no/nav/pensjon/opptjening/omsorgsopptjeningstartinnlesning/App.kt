package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication


@SpringBootApplication
//@EnableJwtTokenValidation
class App


fun main(args: Array<String>) {
    runApplication<App>(*args)
}