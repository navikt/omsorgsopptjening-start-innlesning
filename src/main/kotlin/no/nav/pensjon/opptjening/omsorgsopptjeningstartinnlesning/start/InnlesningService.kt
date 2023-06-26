package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.start

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class InnlesningService(val barnetrygdClient: BarnetrygdClient) {
    companion object {
        private val logger = LoggerFactory.getLogger(InnlesningService::class.java)
    }

    fun initierSendingAvIdenter(ar: Int) {
        barnetrygdClient.initierSendingAvIdenter(ar)
    }

    fun hentBarnetrygdDtaljer(ident: String, ar: Int) {
        barnetrygdClient.hentBarnetrygdDetaljer(ident, ar)
    }
}