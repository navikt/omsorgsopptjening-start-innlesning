package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.pdl

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.PersonId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.person.model.PersonOppslag
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.person.model.PersonOppslagException
import org.springframework.stereotype.Service

@Service
class PdlService(
    private val pdlClient: PdlClient
) : PersonOppslag {

    override fun hentPerson(fnr: String): PersonId {
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