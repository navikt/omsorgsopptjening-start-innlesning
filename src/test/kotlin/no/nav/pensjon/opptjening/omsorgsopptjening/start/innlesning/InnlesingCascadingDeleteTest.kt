package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.Barnetrygdmottaker
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.BarnetrygdmottakerRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.SpringContextTest
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InnlesingCascadingDeleteTest : SpringContextTest.NoKafka() {
    @Autowired
    private lateinit var innlesingRepository: InnlesingRepository

    @Autowired
    private lateinit var barnetrygdmottakerRepository: BarnetrygdmottakerRepository

    @Test
    fun `invalidering av innlesing sletter alle barnetrygdmottakere knyttet til innlesingen`() {
        val a = Innlesing(
            id = InnlesingId.generate(),
            år = 2023
        )

        val b = Barnetrygdmottaker(
            ident = "12345678910",
            correlationId = CorrelationId.generate(),
            innlesingId = a.id
        )

        val aa = innlesingRepository.bestilt(a)
        val bb = barnetrygdmottakerRepository.save(b)

        assertEquals(aa, innlesingRepository.finn(a.id.toString()))
        assertEquals(bb, barnetrygdmottakerRepository.find(bb.id!!))

        innlesingRepository.invalider(aa.id.toUUID())
        assertNull(barnetrygdmottakerRepository.find(bb.id!!))
    }
}