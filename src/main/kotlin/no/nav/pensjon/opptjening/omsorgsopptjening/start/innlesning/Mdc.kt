package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import org.slf4j.MDC

object Mdc {
    fun scopedMdc(key: String, value: String, block: () -> Any){
        return MDC.putCloseable(key, value).use { block() }
    }

    fun getOrCreateCorrelationId(): String {
        return MDC.get(CorrelationId.name) ?: CorrelationId.generate()
    }
}