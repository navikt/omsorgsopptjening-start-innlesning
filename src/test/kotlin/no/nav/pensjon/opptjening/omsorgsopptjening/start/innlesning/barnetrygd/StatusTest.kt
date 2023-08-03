package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.IllegalArgumentException

class StatusTest {

    @Test
    fun `Gitt en ny status saa skal den kunne retryes 2 ganger for den feiler`() {
        val bt = Barnetrygdmottaker("12345678910", 2023, "xx")
        bt.retry()
        bt.retry()
        Assertions.assertInstanceOf(Status.Retry::class.java, bt.status)
        bt.retry()
        Assertions.assertInstanceOf(Status.Feilet::class.java, bt.status)
    }

    @Test
    fun `Gitt en ferdig status saa skal den ikke endre status til retry`() {
        val bt = Barnetrygdmottaker("12345678910", 2023, "xx")
        bt.ferdig()
        assertThrows<IllegalArgumentException> {
            bt.retry()
        }
    }

    @Test
    fun `Gitt en retry status saa skal den kunne endre status til ferdig`() {
        val bt = Barnetrygdmottaker("12345678910", 2023, "xx")
        bt.retry()
        bt.ferdig()
        Assertions.assertInstanceOf(Status.Ferdig::class.java, bt.status)
    }

    @Test
    fun `Gitt en feilet status saa skal den kunne endre status til ferdig`() {
        val bt = Barnetrygdmottaker("12345678910", 2023, "xx")
        bt.retry()
        bt.retry()
        bt.retry()
        Assertions.assertInstanceOf(Status.Feilet::class.java, bt.status)
        assertThrows<IllegalArgumentException> {
            bt.ferdig()
        }
    }
}