package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.PersonId

object PersonSerialization {
    private val objectMapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    fun PersonId.toJson(): String {
        return objectMapper.writeValueAsString(this)
    }

    fun String.toPerson(): PersonId {
        return objectMapper.readValue<PersonId>(this)
    }
}