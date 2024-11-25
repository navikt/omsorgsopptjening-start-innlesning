package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import com.google.common.cache.CacheBuilder
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.pdl.PdlService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class PersonIdService(
    val pdlService: PdlService,
) {
    val cache =
        CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(30)
            .build<Ident, MedRådata<PersonId>>()

    fun personFromIdent(fnr: Ident): MedRådata<PersonId>? {
        return when (val personIdOgRådata: MedRådata<PersonId>? = cache.getIfPresent(fnr)) {
            is MedRådata<PersonId> -> personIdOgRådata
            null -> {
                val personId = pdlService.hentPerson(fnr)
                personId.value.historiske.forEach {
                    cache.put(Ident(it), personId)
                }
                personId
            }

            else -> { // har ingen funksjon, men la til for at det skal kompilere
                throw RuntimeException("cache inneholder ukjent verdi $personIdOgRådata")
            }
        }
    }

    fun clearCache() {
        cache.invalidateAll()
    }

}