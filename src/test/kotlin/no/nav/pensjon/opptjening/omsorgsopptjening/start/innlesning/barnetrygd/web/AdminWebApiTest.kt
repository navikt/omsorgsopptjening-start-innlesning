package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.web

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.SpringContextTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.*

class AdminWebApiTest : SpringContextTest.NoKafka() {

    @Autowired
    private lateinit var adminWebApi: AdminWebApi

    @Test
    fun `kan parse en liste med UUIDer`() {
        val uuids = adminWebApi.parseUUIDListe("\"a7368db4-ecf2-494a-a7aa-95b9363ad0a0\",")
        assertThat(uuids)
            .hasSize(1)
            .contains(UUID.fromString("a7368db4-ecf2-494a-a7aa-95b9363ad0a0"))
    }
}