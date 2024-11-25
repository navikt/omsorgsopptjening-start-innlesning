package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

interface PersonOppslag {
    fun hentPerson(fnr: Ident): MedRådata<PersonId>
}

data class PersonOppslagException(
    val msg: String,
    val throwable: Throwable
) : RuntimeException(msg, throwable)