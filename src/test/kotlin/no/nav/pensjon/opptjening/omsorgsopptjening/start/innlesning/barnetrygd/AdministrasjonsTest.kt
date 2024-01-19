package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import org.junit.jupiter.api.BeforeEach

class AdministrasjonsTest : SpringContextTest.NoKafka() {

    companion object {
        const val OPPTJENINGSÅR = 2020
    }

    @BeforeEach
    fun beforeEach() {
    }
}