package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.web.dto

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Feilinformasjon
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Barnetrygdinformasjon
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Barnetrygdmottaker
import java.util.UUID

data class FeiletMottakerResponse(
    val mottakerId: UUID,
    val opptjeningsAr: Int,
    val status: String,
    val informasjonsStatus: String? = null,
    val exceptionType: String? = null,
    val exceptionMessage: String? = null,
) {
    companion object {
        fun from(
            mottaker: Barnetrygdmottaker.Mottatt,
            informasjon: Barnetrygdinformasjon?,
        ): FeiletMottakerResponse {
            val feilinformasjon = informasjon?.feilinformasjon?.firstOrNull()
            return FeiletMottakerResponse(
                mottakerId = mottaker.id,
                opptjeningsAr = mottaker.år,
                status = mottaker.status.navn(),
                informasjonsStatus = informasjon?.status?.navn(),
                exceptionType = feilinformasjon?.exceptionType(),
                exceptionMessage = feilinformasjon?.exceptionMessage(),
            )
        }
    }
}

private fun Barnetrygdmottaker.Status.navn(): String {
    return when (this) {
        is Barnetrygdmottaker.Status.Klar -> "Klar"
        is Barnetrygdmottaker.Status.Ferdig -> "Ferdig"
        is Barnetrygdmottaker.Status.Retry -> "Retry"
        is Barnetrygdmottaker.Status.Avsluttet -> "Avsluttet"
        is Barnetrygdmottaker.Status.Stoppet -> "Stoppet"
        is Barnetrygdmottaker.Status.Feilet -> "Feilet"
    }
}

private fun Barnetrygdinformasjon.Status.navn(): String {
    return when (this) {
        Barnetrygdinformasjon.Status.KLAR -> "Klar"
        Barnetrygdinformasjon.Status.SENDT -> "Sendt"
        Barnetrygdinformasjon.Status.IKKE_SEND -> "Ikke send"
    }
}

private fun Feilinformasjon.exceptionType(): String {
    return when (this) {
        is Feilinformasjon.UgyldigIdent -> exceptionType
        is Feilinformasjon.FeilIDataGrunnlag -> exceptionType
        is Feilinformasjon.OverlappendeBarnetrygdperioder -> exceptionType
        is Feilinformasjon.OverlappendeHjelpestønadperioder -> exceptionType
    }
}

private fun Feilinformasjon.exceptionMessage(): String {
    return when (this) {
        is Feilinformasjon.UgyldigIdent -> exceptionMessage
        is Feilinformasjon.FeilIDataGrunnlag -> exceptionMessage
        is Feilinformasjon.OverlappendeBarnetrygdperioder -> exceptionMessage
        is Feilinformasjon.OverlappendeHjelpestønadperioder -> exceptionMessage
    }
}
