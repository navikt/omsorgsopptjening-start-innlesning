package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StatusTest {

    @Test
    fun `Gitt en ny status saa skal den kunne retryes 3 ganger for den feiler`() {
        Barnetrygdmottaker(
            "12345678910",
            2023,
            "xx"
        ).also { assertInstanceOf(Barnetrygdmottaker.Status.Klar::class.java, it.status) }
            .retry("").also { assertInstanceOf(Barnetrygdmottaker.Status.Retry::class.java, it.status) }
            .retry("").also { assertInstanceOf(Barnetrygdmottaker.Status.Retry::class.java, it.status) }
            .retry("").also { assertInstanceOf(Barnetrygdmottaker.Status.Retry::class.java, it.status) }
            .retry("").also { assertInstanceOf(Barnetrygdmottaker.Status.Feilet::class.java, it.status) }
    }

    @Test
    fun `Gitt en ferdig status saa skal den ikke endre status til retry`() {
        assertThrows<IllegalArgumentException> {
            Barnetrygdmottaker("12345678910", 2023, "xx")
                .ferdig().also { assertInstanceOf(Barnetrygdmottaker.Status.Ferdig::class.java, it.status) }
                .retry("")
        }
    }

    @Test
    fun `Gitt en retry status saa skal den kunne endre status til ferdig`() {
        Barnetrygdmottaker("12345678910", 2023, "xx")
            .retry("").also { assertInstanceOf(Barnetrygdmottaker.Status.Retry::class.java, it.status) }
            .ferdig().also { assertInstanceOf(Barnetrygdmottaker.Status.Ferdig::class.java, it.status) }
    }

    @Test
    fun `Gitt en feilet status saa skal den ikke kunne endre status til ferdig`() {
        assertThrows<IllegalArgumentException> {
            Barnetrygdmottaker("12345678910", 2023, "xx")
                .retry("").also { assertInstanceOf(Barnetrygdmottaker.Status.Retry::class.java, it.status) }
                .retry("").also { assertInstanceOf(Barnetrygdmottaker.Status.Retry::class.java, it.status) }
                .retry("").also { assertInstanceOf(Barnetrygdmottaker.Status.Retry::class.java, it.status) }
                .retry("").also { assertInstanceOf(Barnetrygdmottaker.Status.Feilet::class.java, it.status) }
                .ferdig()
        }
    }
}