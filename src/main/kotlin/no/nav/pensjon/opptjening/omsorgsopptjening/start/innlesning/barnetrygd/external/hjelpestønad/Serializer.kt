package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.hjelpestønad

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule

object Serializer {
    private val mapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    fun HentHjelpestønadQuery.toJson(): String {
        return mapper.writeValueAsString(this)
    }

    fun String.toHentHjelpestønadQuery(): HentHjelpestønadQuery {
        return mapper.readValue(this, HentHjelpestønadQuery::class.java)
    }
}