package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.PersonId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.PersonSerialization.toJson
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.PersonSerialization.toPerson
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PersonIdSerializationTest {
    @Test
    fun `kan serialisere og deserialisere en person med kun gjeldende ident`() {
        val personId = PersonId(
            fnr = "12345678901",
            historiske = setOf("12345678901"),
        )
        val json = personId.toJson()
        println(json)
        val personToFromString = json.toPerson()
        assertThat(personToFromString).isEqualTo(personId)
    }
}