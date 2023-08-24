package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class StatusTest {

    private val barnetrygdmottaker = Barnetrygdmottaker(
        ident = "12345678910",
        år = 2023,
        correlationId = UUID.randomUUID(),
        requestId = UUID.randomUUID().toString()
    )

    @Test
    fun `Gitt en ny status saa skal den kunne retryes 3 ganger for den feiler`() {
        barnetrygdmottaker.also { assertInstanceOf(Barnetrygdmottaker.Status.Klar::class.java, it.status) }
            .retry("").also { assertInstanceOf(Barnetrygdmottaker.Status.Retry::class.java, it.status) }
            .retry("").also { assertInstanceOf(Barnetrygdmottaker.Status.Retry::class.java, it.status) }
            .retry("").also { assertInstanceOf(Barnetrygdmottaker.Status.Retry::class.java, it.status) }
            .retry("").also { assertInstanceOf(Barnetrygdmottaker.Status.Feilet::class.java, it.status) }
    }

    @Test
    fun `Gitt en ferdig status saa skal den ikke endre status til retry`() {
        assertThrows<IllegalArgumentException> {
            barnetrygdmottaker
                .ferdig().also { assertInstanceOf(Barnetrygdmottaker.Status.Ferdig::class.java, it.status) }
                .retry("")
        }
    }

    @Test
    fun `Gitt en retry status saa skal den kunne endre status til ferdig`() {
        barnetrygdmottaker
            .retry("").also { assertInstanceOf(Barnetrygdmottaker.Status.Retry::class.java, it.status) }
            .ferdig().also { assertInstanceOf(Barnetrygdmottaker.Status.Ferdig::class.java, it.status) }
    }

    @Test
    fun `Gitt en feilet status saa skal den ikke kunne endre status til ferdig`() {
        assertThrows<IllegalArgumentException> {
            barnetrygdmottaker
                .retry("").also { assertInstanceOf(Barnetrygdmottaker.Status.Retry::class.java, it.status) }
                .retry("").also { assertInstanceOf(Barnetrygdmottaker.Status.Retry::class.java, it.status) }
                .retry("").also { assertInstanceOf(Barnetrygdmottaker.Status.Retry::class.java, it.status) }
                .retry("").also { assertInstanceOf(Barnetrygdmottaker.Status.Feilet::class.java, it.status) }
                .ferdig()
        }
    }
}