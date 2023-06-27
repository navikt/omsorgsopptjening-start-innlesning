package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.start

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class IntegrationRepoTest: SpringContextTest.WithKafka() {

    @Autowired
    lateinit var barnetrygdmottakerRepository: BarnetrygdmottakerRepository

    @Test
    fun integrasjonstest() {
        val melding = KafkaListener.BarnetrygdMottakerMelding("12345678910", 2022)
        sendBarnetrygdMottakerKafka(melding)
        barnetrygdmottakerRepository.findAll().single().also {
            assertEquals(it.ident, melding.ident)
            assertEquals(it.ar, melding.ar)
            assertNotNull(it.id)
        }
    }
}