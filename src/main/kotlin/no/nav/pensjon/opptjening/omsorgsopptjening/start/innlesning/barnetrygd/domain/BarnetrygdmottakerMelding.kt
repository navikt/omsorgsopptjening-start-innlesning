package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId

sealed class BarnetrygdmottakerMelding {
    abstract val correlationId: CorrelationId
    abstract val innlesingId: InnlesingId
    abstract val forventetAntallIdenter: Int

    data class Start(
        override val correlationId: CorrelationId,
        override val innlesingId: InnlesingId,
        override val forventetAntallIdenter: Int
    ) : BarnetrygdmottakerMelding()

    data class Data(
        val personIdent: String,
        override val correlationId: CorrelationId,
        override val innlesingId: InnlesingId,
        override val forventetAntallIdenter: Int
    ) : BarnetrygdmottakerMelding()

    data class Slutt(
        override val correlationId: CorrelationId,
        override val innlesingId: InnlesingId,
        override val forventetAntallIdenter: Int
    ) : BarnetrygdmottakerMelding()
}