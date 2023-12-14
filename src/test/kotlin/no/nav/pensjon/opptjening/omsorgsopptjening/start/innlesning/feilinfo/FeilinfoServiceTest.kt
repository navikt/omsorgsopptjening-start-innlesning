package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.feilinfo

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.SpringContextTest
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import kotlin.test.Test

// @Component
class FeilinfoServiceTest(
//    private val feilinfoService: FeilinfoService,
//    private var feilinfoRepository: FeilinfoRepository,
) : SpringContextTest.NoKafka() {

    @Autowired
    private lateinit var feilinfoService: FeilinfoService

    @Autowired
    private lateinit var feilinfoRepository: FeilinfoRepository

    @Test
    fun testEnkelFeilInfo() {
        feilinfoService.lagre("hello")
    }

    @Test
    fun testForLangMelding() {
        feilinfoService.lagre("helloworld".repeat(10000))
    }
}