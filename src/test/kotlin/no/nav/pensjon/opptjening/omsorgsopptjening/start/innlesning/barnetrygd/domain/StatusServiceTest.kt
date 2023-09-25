package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.SpringContextTest
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdInnlesingRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdmottakerRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.databasecontainer.PostgresqlTestContainer
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.support.TransactionTemplate

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
object StatusServiceTest : SpringContextTest.NoKafka() {


    //    companion object {
    private lateinit var innlesingRepository: BarnetrygdInnlesingRepository

    private lateinit var barnetrygdmottakerRepository: BarnetrygdmottakerRepository

    private lateinit var statusService: StatusService

    @BeforeAll
    fun beforeAll() {
        val dataSource = PostgresqlTestContainer.createInstance("test-status")
        val flyway =
            Flyway.configure()
            .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
        flyway.migrate()
        innlesingRepository =
            BarnetrygdInnlesingRepository(NamedParameterJdbcTemplate(dataSource))
        statusService = StatusService(innlesingRepository, TransactionTemplate())
    }

    //    @Disabled
    @Test
    fun testFantIngenInnlesninger() {
        val status = statusService.checkStatus()
        assertThat(status).isEqualTo(ApplicationStatus.IkkeKjort)
    }
}