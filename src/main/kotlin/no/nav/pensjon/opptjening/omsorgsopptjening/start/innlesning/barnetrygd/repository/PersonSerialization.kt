package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Person

object PersonSerialization {
    val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    fun Person.toJson(): String {
        return objectMapper.writeValueAsString(this)
    }

    fun String.toPerson(): Person {
        return objectMapper.readValue<Person>(this)
    }
}