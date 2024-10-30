package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.pdl

class IdentHistorikk(
    private val identer: Set<PdlIdent.FolkeregisterPdlIdent>
) {
    fun gjeldende(): PdlIdent.FolkeregisterPdlIdent {
        return identer.singleOrNull { it is PdlIdent.FolkeregisterPdlIdent.Gjeldende }
            ?: throw IdentHistorikkManglerGjeldendeException()
    }

    fun historikk(): Set<PdlIdent.FolkeregisterPdlIdent> {
        return identer
    }

    class IdentHistorikkManglerGjeldendeException(msg: String = "Fant ingen gjeldende identer i identhistorikk") :
        RuntimeException(msg)
}