package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

class Person(
    val identhistorikk: IdentHistorikk,
) {
    val fnr = identhistorikk.gjeldende().ident

    override fun equals(other: Any?) = this === other || (other is Person && identifisertAv(other.fnr))
    override fun hashCode() = fnr.hashCode()

    fun identifisertAv(fnr: String): Boolean {
        return identhistorikk.identifiseresAv(fnr)
    }
}


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
