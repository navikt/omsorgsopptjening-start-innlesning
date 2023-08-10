package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication


@SpringBootApplication
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}