package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.pdl

class IdentHistorikk(
    private val identer: Set<Ident.FolkeregisterIdent>
) {
    fun gjeldende(): Ident.FolkeregisterIdent {
        return identer.singleOrNull { it is Ident.FolkeregisterIdent.Gjeldende }
            ?: throw IdentHistorikkManglerGjeldendeException()
    }

    fun historikk(): Set<Ident.FolkeregisterIdent> {
        return identer
    }

    fun identifiseresAv(ident: String): Boolean {
        return identer.map { it.ident }.contains(ident)
    }

    class IdentHistorikkManglerGjeldendeException(msg: String = "Fant ingen gjeldende identer i identhistorikk") :
        RuntimeException(msg)
}