package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.pdl.PdlService
import org.springframework.stereotype.Service

@Service
class PersonIdService(
    val pdlService: PdlService,
) {
    val personIdMap: MutableMap<String, PersonId> = mutableMapOf()

    // TODO: håndter personer som ikke finnes i PDL
    fun personFromIdent(fnr: String): PersonId? {
        if (!personIdMap.containsKey(fnr)) {
            val personId = pdlService.hentPerson(fnr)
            personId.historiske.forEach {
                personIdMap[it] = personId
            }
        }
        return personIdMap[fnr]
    }

}