package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.person.external.pdl

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Person
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.person.model.PersonOppslag
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.person.model.PersonOppslagException
import org.springframework.stereotype.Service

@Service
internal class PdlService(
    private val pdlClient: PdlClient
) : PersonOppslag {

    override fun hentPerson(fnr: String): Person {
        try {
            val pdlResponse = pdlClient.hentPerson(fnr = fnr)

            val hentPersonQueryResponse = pdlResponse?.data?.hentPerson ?: throw PdlException(pdlResponse?.error)

            return hentPersonQueryResponse.toDomain()
        } catch (ex: Throwable) {
            throw PersonOppslagException("Feil ved henting av person", ex)
        }
    }
}

internal class PdlException(pdlError: PdlError?) : RuntimeException(pdlError?.message ?: "Unknown error from PDL") {
    val code: PdlErrorCode? = pdlError?.extensions?.code
}

internal class PdlMottatDataException(message: String) : RuntimeException(message)