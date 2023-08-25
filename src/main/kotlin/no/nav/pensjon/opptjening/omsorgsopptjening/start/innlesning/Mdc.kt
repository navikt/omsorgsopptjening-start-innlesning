package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import org.slf4j.MDC

object Mdc {
    fun <T> scopedMdc(correlationId: CorrelationId, block: (correlationId: CorrelationId) -> T): T {
        return MDC.putCloseable(CorrelationId.identifier, correlationId.toString()).use { block(correlationId) }
    }

    fun <T> scopedMdc(innlesingId: InnlesingId, block: (innlesingId: InnlesingId) -> T): T {
        return MDC.putCloseable(InnlesingId.identifier, innlesingId.toString()).use { block(innlesingId) }
    }

    fun getCorrelationId(): String {
        return MDC.get(CorrelationId.identifier).toString()
    }

    fun getInnlesingId(): String? {
        return MDC.get(InnlesingId.identifier).toString()
    }
}