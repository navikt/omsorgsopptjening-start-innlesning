package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.pdl

import com.fasterxml.jackson.annotation.JsonProperty

data class PdlError(val message: String, val extensions: Extensions)

data class Extensions(val code: PdlErrorCode)

enum class PdlErrorCode {
    @JsonProperty("unauthenticated")
    UNAUTHENTICATED,

    @JsonProperty("unauthorized")
    UNAUTHORIZED,

    @JsonProperty("not_found")
    NOT_FOUND,

    @JsonProperty("bad_request")
    BAD_REQUEST,

    @JsonProperty("server_error")
    SERVER_ERROR,
}