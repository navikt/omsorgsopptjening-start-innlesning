package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.pdl

sealed class PdlIdent {
    abstract val ident: String

    sealed class FolkeregisterPdlIdent : PdlIdent() {

        data class Gjeldende(
            override val ident: String
        ) : FolkeregisterPdlIdent()

        data class Historisk(
            override val ident: String
        ) : FolkeregisterPdlIdent()
    }
}