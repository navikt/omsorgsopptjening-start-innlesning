package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdInnlesingRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdmottakerRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class BarnetrygdmottakerMessageHandler(
    private val innlesingRepository: BarnetrygdInnlesingRepository,
    private val barnetrygdmottakerRepository: BarnetrygdmottakerRepository
) {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }

    @Throws(
        BarnetrygdInnlesingException.EksistererIkke::class, BarnetrygdInnlesingException.UgyldigTistand::class
    )
    fun handle(melding: BarnetrygdmottakerMelding) {
        finnInnlesing(melding.innlesingId)
            .also { it.håndterMelding(melding) }
    }


    @Throws(BarnetrygdInnlesingException.EksistererIkke::class)
    private fun finnInnlesing(innlesingId: InnlesingId): BarnetrygdInnlesing {
        return innlesingRepository.finn(innlesingId.toString())
            ?: throw BarnetrygdInnlesingException.EksistererIkke(innlesingId.toString())
    }

    @Throws(BarnetrygdInnlesingException.UgyldigTistand::class)
    private fun BarnetrygdInnlesing.håndterMelding(melding: BarnetrygdmottakerMelding) {
        when (melding) {
            is BarnetrygdmottakerMelding.Start -> håndterStartmelding(melding)
            is BarnetrygdmottakerMelding.Data -> håndterDatamelding(melding)
            is BarnetrygdmottakerMelding.Slutt -> håndterSluttmelding(melding)
        }
    }

    private fun BarnetrygdInnlesing.håndterStartmelding(melding: BarnetrygdmottakerMelding.Start) {
        try {
            log.info("Starter ny innlesing, id: ${this.id}")
            innlesingRepository.start(startet())
        } catch (ex: BarnetrygdInnlesing.UgyldigTilstand) {
            throw BarnetrygdInnlesingException.UgyldigTistand(this.id.toString(), melding::class.java.simpleName)
        }
    }

    private fun BarnetrygdInnlesing.håndterDatamelding(melding: BarnetrygdmottakerMelding.Data) {
        try {
            log.info("Mottatt melding om barnetrygdmottaker")
            mottaData().also {
                barnetrygdmottakerRepository.insert(
                    toBarnetrygdmottaker(
                        personident = melding.personIdent,
                        correlationId = melding.correlationId,
                        innlesingId = melding.innlesingId
                    )
                )
                log.info("Melding prosessert")
            }
        } catch (ex: BarnetrygdInnlesing.UgyldigTilstand) {
            throw BarnetrygdInnlesingException.UgyldigTistand(this.id.toString(), melding::class.java.simpleName)
        }
    }

    private fun BarnetrygdInnlesing.håndterSluttmelding(melding: BarnetrygdmottakerMelding.Slutt) {
        try {
            log.info("Fullført innlesing, id:  ${this.id}")
            innlesingRepository.fullført(ferdig())
        } catch (ex: BarnetrygdInnlesing.UgyldigTilstand) {
            throw BarnetrygdInnlesingException.UgyldigTistand(this.id.toString(), melding::class.java.simpleName)
        }
    }

    private fun toBarnetrygdmottaker(
        personident: String,
        correlationId: CorrelationId,
        innlesingId: InnlesingId,
    ): Barnetrygdmottaker {
        return Barnetrygdmottaker(
            ident = personident,
            correlationId = correlationId,
            innlesingId = innlesingId
        )
    }
}

sealed class BarnetrygdInnlesingException : RuntimeException() {
    data class EksistererIkke(val id: String) : RuntimeException()
    data class UgyldigTistand(
        val id: String,
        val meldingstype: String
    ) : RuntimeException()
}