package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.person.external.pdl

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Ident
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.IdentHistorikk
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Person
import java.time.LocalDateTime

internal data class PdlResponse(
    val data: PdlData,
    private val errors: List<PdlError>? = null
) {
    val error: PdlError? = errors?.firstOrNull()
}

internal data class PdlData(
    val hentPerson: HentPersonQueryResponse?
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class HentPersonQueryResponse(
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
                        Ident.FolkeregisterIdent.Gjeldende(it.identifikasjonsnummer)
                    }

                    Folkeregisteridentifikator.Status.OPPHOERT -> {
                        Ident.FolkeregisterIdent.Historisk(it.identifikasjonsnummer)
                    }
                }
            }.toSet()
        )
    }

    fun toDomain(): Person {
        return Person(
            identhistorikk = identhistorikk(),
        )
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class Folkeregisteridentifikator(
    val identifikasjonsnummer: String,
    val status: Status,
    val type: Type,
    val metadata: Metadata,
    val folkeregistermetadata: Folkeregistermetadata? = null,
) {
    fun erGjeldende(): Boolean {
        return status == Status.I_BRUK
    }

    enum class Status { I_BRUK, OPPHOERT }
    enum class Type { FNR, DNR }
}

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class Metadata(
    val historisk: Boolean,
    val master: String,
    val endringer: List<Endring> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class Folkeregistermetadata(
    val ajourholdstidspunkt: LocalDateTime? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class Endring(
    val registrert: LocalDateTime
)