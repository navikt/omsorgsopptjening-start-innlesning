package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.SpringContextTest
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdInnlesingRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdmottakerRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.databasecontainer.PostgresqlTestContainer
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
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
        statusService = StatusService(innlesingRepository)
    }

    //    @Disabled
    @Test
    @Order(0)
    fun testFantIngenInnlesinger() {
        val status = statusService.checkStatus()
        assertThat(status).isEqualTo(ApplicationStatus.IkkeKjort)
    }

    @Test
    @Order(1)
    fun testSisteInnlesingForGammel() {
        innlesingRepository.bestilt(BarnetrygdInnlesing.Bestilt(
            id = InnlesingId.generate(),
            år = 2001,
            forespurtTidspunkt = Instant.now().minus(Duration.ofDays(500))
        ))
        val status = statusService.checkStatus()
        assertThat(status)
            .isInstanceOf(ApplicationStatus.Feil::class.java)
            .extracting("feil")
            .isEqualTo(listOf("For lenge siden siste innlesing"))
    }

    @Test
    @Order(2)
    fun testSisteInnlesingIkkeProsessert() {
        innlesingRepository.bestilt(BarnetrygdInnlesing.Bestilt(
            id = InnlesingId.generate(),
            år = 2001,
            forespurtTidspunkt = Instant.now().minus(Duration.ofHours(3))
        ))
        val status = statusService.checkStatus()
        assertThat(status)
            .isInstanceOf(ApplicationStatus.Feil::class.java)
            .extracting("feil")
            .isEqualTo(listOf("Innlesing er ikke prosessert"))
    }

}