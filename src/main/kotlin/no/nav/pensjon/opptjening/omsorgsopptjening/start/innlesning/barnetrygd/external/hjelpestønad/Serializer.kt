package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.hjelpestønad

import tools.jackson.module.kotlin.jacksonObjectMapper

object Serializer {
    private val mapper = jacksonObjectMapper()
    fun HentHjelpestønadQuery.toJson(): String {
        return mapper.writeValueAsString(this)
    }

    fun String.toHentHjelpestønadQuery(): HentHjelpestønadQuery {
        return mapper.readValue(this, HentHjelpestønadQuery::class.java)
    }
}
