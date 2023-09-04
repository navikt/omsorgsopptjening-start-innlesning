package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.kafka

import java.util.UUID

data class BarnetrygdmottakerKafkaMelding(
    val meldingstype: Type,
    val requestId: UUID,
    val personident: String?
) {
    enum class Type {
        START,
        DATA,
        SLUTT;
    }
}
