package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.monitorering

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.SpringContextTest
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdInnlesing
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Barnetrygdmottaker
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdInnlesingRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdmottakerRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.databasecontainer.PostgresqlTestContainer
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.Duration
import java.time.Instant
import java.util.*

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
object StatusServiceTest : SpringContextTest.NoKafka() {

    private lateinit var innlesingRepository: BarnetrygdInnlesingRepository
    private lateinit var mottakerRepository: BarnetrygdmottakerRepository
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
        mottakerRepository =
            BarnetrygdmottakerRepository(NamedParameterJdbcTemplate(dataSource))
        statusService = StatusService(innlesingRepository, mottakerRepository)
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
        innlesingRepository.bestilt(
            BarnetrygdInnlesing.Bestilt(
                id = InnlesingId.generate(),
                år = 2001,
                forespurtTidspunkt = Instant.now().minus(Duration.ofDays(500))
            )
        )
        val status = statusService.checkStatus()
        assertThat(status)
            .isInstanceOf(ApplicationStatus.Feil::class.java)
            .extracting("feil")
            .isEqualTo("For lenge siden siste innlesing")
    }

    @Test
    @Order(2)
    fun testSisteInnlesingIkkeProsessert() {
        innlesingRepository.bestilt(
            BarnetrygdInnlesing.Bestilt(
                id = InnlesingId.generate(),
                år = 2001,
                forespurtTidspunkt = Instant.now().minus(Duration.ofHours(4))
            )
        )
        val status = statusService.checkStatus()
        assertThat(status)
            .isInstanceOf(ApplicationStatus.Feil::class.java)
            .extracting("feil")
            .isEqualTo("Innlesing er ikke prosessert")
    }

    @Test
    @Order(3)
    fun testMottakereIkkeProsessert() {
        val innlesing = innlesingRepository.bestilt(
            BarnetrygdInnlesing.Bestilt(
                id = InnlesingId.generate(),
                år = 2001,
                forespurtTidspunkt = Instant.now().minus(Duration.ofHours(3))
            )
        )
        val startet = innlesingRepository.start(innlesing.startet(10))
        innlesingRepository.fullført(startet.ferdig())

        val barnetrygdmottaker = Barnetrygdmottaker.Transient(
            ident = "12345123451",
            correlationId = CorrelationId(UUID.randomUUID()),
            innlesingId = innlesing.id,
        )

        val mottatt = mottakerRepository.insert(barnetrygdmottaker)
        mottakerRepository.updateStatus(mottatt.ferdig())

        val antallFerdig = mottakerRepository.finnAntallMottakereMedStatusForInnlesing(Barnetrygdmottaker.KortStatus.FERDIG, innlesing.id)

        println(";;; $antallFerdig / ${startet.forventetAntallIdentiteter}")
        val status = statusService.checkStatus()
        assertThat(status)
            .isInstanceOf(ApplicationStatus.Feil::class.java)
            .extracting("feil")
            .isEqualTo("Alle mottakere er ikke prosessert")
    }

    @Test
    @Order(3)
    fun testFeiledeMottakere() {
        // en ny innlesing for ikke å trigge andre feil
        val innlesingGammel = innlesingRepository.bestilt(
            BarnetrygdInnlesing.Bestilt(
                id = InnlesingId.generate(),
                år = 2001,
                forespurtTidspunkt = Instant.now().minus(Duration.ofDays(5))
            )
        )

        val innlesingNy = innlesingRepository.bestilt(
            BarnetrygdInnlesing.Bestilt(
                id = InnlesingId.generate(),
                år = 2001,
                forespurtTidspunkt = Instant.now().minus(Duration.ofMinutes(5))
            )
        )

        val barnetrygdmottaker = Barnetrygdmottaker.Transient(
            ident = "12345123451",
            correlationId = CorrelationId(UUID.randomUUID()),
            innlesingId = innlesingGammel.id, // tilhører ikke siste innlesing
        )

        val mottatt = mottakerRepository.insert(barnetrygdmottaker)
        mottakerRepository.updateStatus(mottatt.retry("1"))
        mottakerRepository.updateStatus(mottatt.retry("2"))
        mottakerRepository.updateStatus(mottatt.retry("3"))
        mottakerRepository.updateStatus(mottatt.retry("feilet"))
        val feilet = mottakerRepository.find(mottatt.id)
        println("X5 $feilet")

        val status = statusService.checkStatus()
        assertThat(status)
            .isInstanceOf(ApplicationStatus.Feil::class.java)
            .extracting("feil")
            .isEqualTo("Alle mottakere er ikke prosessert")
    }

}