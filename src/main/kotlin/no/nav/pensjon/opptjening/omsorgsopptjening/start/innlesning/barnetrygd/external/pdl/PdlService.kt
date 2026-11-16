package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.pdl

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Ident
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.MedRådata
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.PersonId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.PersonOppslag
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.PersonOppslagException
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.metrics.Metrikker
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PdlService(
    private val pdlClient: PdlClient,
    private val metrikker: Metrikker,
) : PersonOppslag {
    companion object {
        private val log = LoggerFactory.getLogger(PdlService::class.java)
        private val secureLog = LoggerFactory.getLogger("secure")
    }

    override fun hentPerson(fnr: Ident): MedRådata<PersonId> {
        try {

            val pdlResponse = metrikker.målHentPdl {
                pdlClient.hentPerson(fnr = fnr)
            }

            val hentPersonQueryResponse =
                pdlResponse?.value?.data?.hentPerson
                    ?: throw PdlException(
                        pdlResponse?.value?.error,
                        rådata = pdlResponse?.rådata ?: emptyList()
                    )

            return MedRådata(
                value = hentPersonQueryResponse.toDomain(),
                rådata = pdlResponse.rådata,
            )
        } catch (ex: PdlException) {
            secureLog.error("Feil ved oppslag av person", ex)
            throw PersonOppslagException(
                msg = "Feil ved henting av person",
                throwable = ex,
                rådata = ex.rådata
            )
        } catch (ex: Throwable) {
            secureLog.error("Feil ved oppslag av person", ex)
            throw PersonOppslagException(
                msg = "Feil ved henting av person",
                throwable = ex,
                rådata = emptyList()
            )
        }
    }
}

