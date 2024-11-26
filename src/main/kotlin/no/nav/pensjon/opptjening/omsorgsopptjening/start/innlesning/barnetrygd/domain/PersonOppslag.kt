package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.RådataFraKilde

interface PersonOppslag {
    fun hentPerson(fnr: Ident): MedRådata<PersonId>
}

data class PersonOppslagException(
    val msg: String,
    val throwable: Throwable,
    val rådata: List<RådataFraKilde>,
) : RuntimeException(msg, throwable)