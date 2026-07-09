package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.PersonId
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

object PersonSerialization {
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    fun PersonId.toJson(): String {
        return objectMapper.writeValueAsString(this)
    }

    fun String.toPerson(): PersonId {
        return objectMapper.readValue<PersonId>(this)
    }
}
