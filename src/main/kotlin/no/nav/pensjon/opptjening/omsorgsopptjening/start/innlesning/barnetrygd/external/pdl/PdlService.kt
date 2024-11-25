package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.pdl

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PdlService(
    private val pdlClient: PdlClient
) : PersonOppslag {
    companion object {
        private val log = LoggerFactory.getLogger(PdlService::class.java)
        private val secureLog = LoggerFactory.getLogger("secure")
    }

    override fun hentPerson(fnr: Ident): MedRådata<PersonId> {
        try {
            val pdlResponse = pdlClient.hentPerson(fnr = fnr)

            val hentPersonQueryResponse =
                pdlResponse?.value?.data?.hentPerson
                    ?: throw PdlException(pdlResponse?.value?.error)

            return MedRådata(
                value = hentPersonQueryResponse.toDomain(),
                rådata = pdlResponse.rådata,
            )
        } catch (ex: Throwable) {
            secureLog.error("Feil ved oppslag av person", ex)
            throw PersonOppslagException("Feil ved henting av person", ex)
        }
    }
}

