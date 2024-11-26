package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.pdl

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.RådataFraKilde

internal class PdlException(
    pdlError: PdlError?,
    val rådata: List<RådataFraKilde>,
) : RuntimeException(pdlError?.message ?: "Unknown error from PDL") {
    val code: PdlErrorCode? = pdlError?.extensions?.code
}