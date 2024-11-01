package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.SpringContextTest
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdInnlesing
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Barnetrygdmottaker
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Ident
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.År
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

class InnlesingRepositoryTest : SpringContextTest.NoKafka() {

    @Autowired
    private lateinit var innlesingRepository: InnlesingRepository

    @Autowired
    private lateinit var barnetrygdmottakerRepository: BarnetrygdmottakerRepository

    @Test
    fun `insert, update, delete`() {
        val bestilt = BarnetrygdInnlesing.Bestilt(
            id = InnlesingId.generate(),
            år = År(2023),
            forespurtTidspunkt = Instant.now()
        )
        val startet = bestilt.startet(1)
        val ferdig = startet.ferdig()

        val b = innlesingRepository.bestilt(bestilt)
        assertThat(innlesingRepository.finn(bestilt.id.toString())).isEqualTo(b)
        val s = innlesingRepository.start(startet)
        assertThat(innlesingRepository.finn(bestilt.id.toString())).isEqualTo(s)
        val f = innlesingRepository.fullført(ferdig)
        assertThat(innlesingRepository.finn(bestilt.id.toString())).isEqualTo(f)
        innlesingRepository.invalider(bestilt.id.toUUID())
        assertThat(innlesingRepository.finn(bestilt.id.toString())).isNull()
    }

    @Test
    fun `invalidering av innlesing sletter alle barnetrygdmottakere knyttet til innlesingen`() {
        val bestilt = BarnetrygdInnlesing.Bestilt(
            id = InnlesingId.generate(),
            år = År(2023),
            forespurtTidspunkt = Instant.EPOCH
        )

        val b = Barnetrygdmottaker.Transient(
            ident = Ident("12345678910"),
            correlationId = CorrelationId.generate(),
            innlesingId = bestilt.id
        )

        val aa = innlesingRepository.bestilt(bestilt)
        val bb = barnetrygdmottakerRepository.insert(b)

        assertThat(innlesingRepository.finn(bestilt.id.toString())).isEqualTo(aa)
        assertThat(barnetrygdmottakerRepository.find(bb.id)).isEqualTo(bb)

        innlesingRepository.invalider(aa.id.toUUID())
        assertThat(barnetrygdmottakerRepository.find(bb.id)).isNull()
    }
}
