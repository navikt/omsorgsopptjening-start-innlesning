package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Innlesing
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.InnlesingRepository
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID

class InnlesingInvalideringTest : SpringContextTest.WithKafka() {

    @Autowired
    private lateinit var innlesingRepository: InnlesingRepository

    @Test
    fun `invaliderer innlesing dersom en record ikke leses ok`() {
        val innlesing = innlesingRepository.bestilt(Innlesing(InnlesingId.generate(), 2020))

        sendStartInnlesingKafka(innlesing.id.toString())

        sendBarnetrygdmottakerDataKafka(
            melding = KafkaMelding(
                meldingstype = KafkaMelding.Type.DATA,
                requestId = UUID.fromString(innlesing.id.toString()),
                personident = "12345678910"
            )
        )

        Thread.sleep(1000)
        assertNotNull(innlesingRepository.finn(innlesing.id.toString()))

        sendBarnetrygdmottakerDataKafka(
            melding = KafkaMelding(
                meldingstype = KafkaMelding.Type.DATA,
                requestId = UUID.fromString(innlesing.id.toString()),
                personident = null
            )
        )

        Thread.sleep(1000)
        assertNull(innlesingRepository.finn(innlesing.id.toString()))

        sendBarnetrygdmottakerDataKafka(
            melding = KafkaMelding(
                meldingstype = KafkaMelding.Type.DATA,
                requestId = UUID.fromString(innlesing.id.toString()),
                personident = "12345678910"
            )
        )
        Thread.sleep(1000)
        assertNull(innlesingRepository.finn(innlesing.id.toString()))

        sendSluttInnlesingKafka(innlesing.id.toString())

        Thread.sleep(1000)
        assertNull(innlesingRepository.finn(innlesing.id.toString()))
    }
}