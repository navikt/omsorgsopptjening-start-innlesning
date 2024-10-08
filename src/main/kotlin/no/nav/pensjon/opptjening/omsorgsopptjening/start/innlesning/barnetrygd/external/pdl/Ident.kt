package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.pdl

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
}