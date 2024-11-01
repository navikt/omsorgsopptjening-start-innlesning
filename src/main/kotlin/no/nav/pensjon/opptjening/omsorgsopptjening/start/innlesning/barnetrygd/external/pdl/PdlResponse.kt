package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.pdl

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Ident
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.PersonId
import java.time.LocalDateTime

data class PdlResponse(
    val data: PdlData,
    private val errors: List<PdlError>? = null
) {
    val error: PdlError? = errors?.firstOrNull()
}

data class PdlData(
    val hentPerson: HentPersonQueryResponse?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class HentPersonQueryResponse(
    val folkeregisteridentifikator: List<Folkeregisteridentifikator>,
) {
    private fun identhistorikk(): IdentHistorikk {
        return folkeregisteridentifikator.identhistorikk()
    }

    private fun List<Folkeregisteridentifikator>.identhistorikk(): IdentHistorikk {
        return IdentHistorikk(
            map {
                when (it.status) {
                    Folkeregisteridentifikator.Status.I_BRUK -> {
                        PdlIdent.FolkeregisterPdlIdent.Gjeldende(it.identifikasjonsnummer)
                    }

                    Folkeregisteridentifikator.Status.OPPHOERT -> {
                        PdlIdent.FolkeregisterPdlIdent.Historisk(it.identifikasjonsnummer)
                    }
                }
            }.toSet()
        )
    }

    fun toDomain(): PersonId {
        println("identhistorikk:gjeldende: ${identhistorikk().gjeldende().ident}")
        println("identhistorikk:historikk: ${identhistorikk().historikk()}")
        return PersonId(
            fnr = Ident(identhistorikk().gjeldende().ident),
            historiske = identhistorikk().historikk().map { it.ident }.toSet()
        )
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class Folkeregisteridentifikator(
    val identifikasjonsnummer: String,
    val status: Status,
    val type: Type,
    val metadata: Metadata,
    val folkeregistermetadata: Folkeregistermetadata? = null,
) {
    enum class Status { I_BRUK, OPPHOERT }
    enum class Type { FNR, DNR }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class Metadata(
    val historisk: Boolean,
    val master: String,
    val endringer: List<Endring> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Folkeregistermetadata(
    val ajourholdstidspunkt: LocalDateTime? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Endring(
    val registrert: LocalDateTime
)