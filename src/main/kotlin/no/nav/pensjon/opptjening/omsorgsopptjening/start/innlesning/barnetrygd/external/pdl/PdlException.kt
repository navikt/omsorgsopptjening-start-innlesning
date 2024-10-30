package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.pdl

internal class PdlException(
    pdlError: PdlError?
) : RuntimeException(pdlError?.message ?: "Unknown error from PDL") {
    val code: PdlErrorCode? = pdlError?.extensions?.code
}