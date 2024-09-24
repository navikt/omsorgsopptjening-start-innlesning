package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

sealed class Ident {
    abstract val ident: String

    sealed class FolkeregisterIdent : Ident() {

        data class Gjeldende(
            override val ident: String
        ) : FolkeregisterIdent()

        data class Historisk(
            override val ident: String
        ) : FolkeregisterIdent()
    }

    data object Ukjent : Ident() {
        override val ident: String = IDENT_UKJENT
    }

    companion object {
        const val IDENT_UKJENT = "IDENT_UKJENT"
    }
}