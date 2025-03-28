package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.InnlesingRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdmottakerRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class BarnetrygdmottakerMessageHandler(
    private val innlesingRepository: InnlesingRepository,
    private val barnetrygdmottakerRepository: BarnetrygdmottakerRepository
) {
    companion object {
        private val log = LoggerFactory.getLogger(BarnetrygdmottakerMessageHandler::class.java)
    }

    @Throws(
        BarnetrygdInnlesingException.EksistererIkke::class, BarnetrygdInnlesingException.UgyldigTistand::class
    )
    fun handle(meldinger: List<BarnetrygdmottakerMelding>) {
        meldinger
            .groupBy { it.innlesingId }
            .forEach { (innlesing, meldinger) ->
                finnInnlesing(innlesing).let { i ->
                    meldinger.filterIsInstance<BarnetrygdmottakerMelding.Start>().forEach { i.håndterStartmelding(it) }
                }
                finnInnlesing(innlesing).also { i ->
                    meldinger.filterIsInstance<BarnetrygdmottakerMelding.Data>().map { i.håndterDatamelding(it) }
                        .also { if (it.isNotEmpty()) barnetrygdmottakerRepository.insertBatch(it) }
                }
                finnInnlesing(innlesing).also { i ->
                    meldinger.filterIsInstance<BarnetrygdmottakerMelding.Slutt>().forEach { i.håndterSluttmelding(it) }
                }
            }
    }


    @Throws(BarnetrygdInnlesingException.EksistererIkke::class)
    private fun finnInnlesing(innlesingId: InnlesingId): BarnetrygdInnlesing {
        return innlesingRepository.finn(innlesingId.toString())
            ?: throw BarnetrygdInnlesingException.EksistererIkke(innlesingId.toString())
    }


    @Throws(BarnetrygdInnlesingException.UgyldigTistand::class)
    private fun BarnetrygdInnlesing.håndterStartmelding(melding: BarnetrygdmottakerMelding.Start) {
        try {
            log.info("Starter ny innlesing, id: ${this.id}")
            innlesingRepository.start(startet(melding.forventetAntallIdenter.toLong()))
        } catch (ex: BarnetrygdInnlesing.UgyldigTilstand) {
            throw BarnetrygdInnlesingException.UgyldigTistand(this.id.toString(), melding::class.java.simpleName)
        }
    }

    @Throws(BarnetrygdInnlesingException.UgyldigTistand::class)
    private fun BarnetrygdInnlesing.håndterDatamelding(melding: BarnetrygdmottakerMelding.Data): Barnetrygdmottaker.Transient {
        return try {
            mottaData().let {
                Barnetrygdmottaker.Transient(
                    ident = Ident(melding.personIdent),
                    correlationId = melding.correlationId,
                    innlesingId = melding.innlesingId
                )
            }
        } catch (ex: BarnetrygdInnlesing.UgyldigTilstand) {
            throw BarnetrygdInnlesingException.UgyldigTistand(this.id.toString(), melding::class.java.simpleName)
        }
    }

    @Throws(BarnetrygdInnlesingException.UgyldigTistand::class)
    private fun BarnetrygdInnlesing.håndterSluttmelding(melding: BarnetrygdmottakerMelding.Slutt) {
        try {
            log.info("Fullført innlesing, id:  ${this.id}")
            ferdig().also {
                if (it.antallIdenterLest != melding.forventetAntallIdenter) {
                    throw BarnetrygdInnlesing.UgyldigTilstand(it.id.toString(), melding::class.java.simpleName)
                }
                innlesingRepository.fullført(it)
            }
        } catch (ex: BarnetrygdInnlesing.UgyldigTilstand) {
            throw BarnetrygdInnlesingException.UgyldigTistand(this.id.toString(), melding::class.java.simpleName)
        }
    }
}

sealed class BarnetrygdInnlesingException : RuntimeException() {
    data class EksistererIkke(val id: String) : RuntimeException()
    data class UgyldigTistand(
        val id: String,
        val meldingstype: String
    ) : RuntimeException()
}