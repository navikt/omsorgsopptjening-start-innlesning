package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.person.external.pdl

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class IdenterResponse(
    val data: IdenterDataResponse? = null,
    private val errors: List<PdlError>? = null
) {
    val error: PdlError? = errors?.firstOrNull()
}

internal data class IdenterDataResponse(
    val hentIdenter: HentIdenter? = null
)

internal data class HentIdenter(
    val identer: List<IdentInformasjon>
)

internal data class IdentInformasjon(
    val ident: String,
    val gruppe: IdentGruppe
)

enum class IdentGruppe {
    AKTORID,
    FOLKEREGISTERIDENT,
    NPID
}

