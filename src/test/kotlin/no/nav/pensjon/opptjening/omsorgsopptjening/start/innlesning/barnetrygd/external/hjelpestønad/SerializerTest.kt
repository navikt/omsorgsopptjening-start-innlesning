package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.hjelpestønad

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.hjelpestønad.Serializer.toHentHjelpestønadQuery
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.hjelpestønad.Serializer.toJson
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.Month

class SerializerTest {
    @Test
    fun `kan serialisere og deserialisere HentHjelpestønadQuery`() {
        val query = HentHjelpestønadQuery(
            fnr = "12345",
            fom = LocalDate.of(2017, Month.FEBRUARY, 10),
            tom = LocalDate.of(2020, Month.OCTOBER, 10),
        )
        val serialisert = query.toJson()
        val deserialisert = serialisert.toHentHjelpestønadQuery()

        assertThat(serialisert).contains("\"2017-02-10\"")
        assertThat(deserialisert).isEqualTo(query)
    }

}