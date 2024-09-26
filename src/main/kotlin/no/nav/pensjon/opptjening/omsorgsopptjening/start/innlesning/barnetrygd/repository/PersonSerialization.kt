package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository

import com.fasterxml.jackson.module.kotlin.jsonMapper
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.mapToJson
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Person

object PersonSerialization {
    fun Person.toJson(): String {
        return this.mapToJson()
    }

    fun String.toPerson(): Person {
        return jsonMapper().readValue(this, Person::class.java)
    }
}