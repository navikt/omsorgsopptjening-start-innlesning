package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.monitorering

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.SpringContextTest
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdInnlesing
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Barnetrygdmottaker
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Ident
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.År
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.InnlesingRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdmottakerRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.databasecontainer.PostgresqlTestContainer
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.Duration
import java.time.Instant
import java.util.*
import javax.sql.DataSource

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
object StatusServiceTest : SpringContextTest.NoKafka() {

    private lateinit var innlesingRepository: InnlesingRepository
    private lateinit var mottakerRepository: BarnetrygdmottakerRepository
    private lateinit var statusService: StatusService
    private lateinit var statusDataSource: DataSource

    @BeforeAll
    fun beforeAll() {
        statusDataSource = PostgresqlTestContainer.createInstance("test-status")
        val flyway =
            Flyway.configure()
                .dataSource(statusDataSource)
                .locations("classpath:db/migration")
                .load()
        flyway.migrate()
        innlesingRepository =
            InnlesingRepository(NamedParameterJdbcTemplate(statusDataSource))
        mottakerRepository =
            BarnetrygdmottakerRepository(NamedParameterJdbcTemplate(statusDataSource))
        statusService = StatusService(innlesingRepository, mottakerRepository)
    }

    @BeforeEach
    fun removeDataFromDB() {
        statusDataSource.connection.apply {
            createStatement().execute(
                """                       
                        DELETE FROM BARNETRYGDINFORMASJON;
                        DELETE FROM BARNETRYGDMOTTAKER;
                        DELETE FROM INNLESING;
                    """
            )
            close()
        }
        // TODO: burde disable setup fra superklassen, sånn at man ikke tømmer en instansen
        // som denne testklassen ikke bruker
    }

    @Test
    @Order(0)
    fun `fant ingen innlesinger`() {
        val status = statusService.checkStatus()
        assertThat(status).isEqualTo(ApplicationStatus.IkkeKjort)
    }

    @Test
    @Order(1)
    fun `siste innlesing er for gammel`() {
        val innlesing = innlesingRepository.bestilt(
            BarnetrygdInnlesing.Bestilt(
                id = InnlesingId.generate(),
                år = År(2001),
                forespurtTidspunkt = Instant.now().minus(Duration.ofDays(500))
            )
        )
        println("innlesing: ${innlesing.id}")
        val status = statusService.checkStatus()
        assertThat(status)
            .isInstanceOf(ApplicationStatus.Feil::class.java)
            .extracting("feil")
            .isEqualTo("For lenge siden siste innlesing")
    }

    @Test
    @Order(2)
    fun `siste innlesing er ikke prosessert`() {
        val innleding = innlesingRepository.bestilt(
            BarnetrygdInnlesing.Bestilt(
                id = InnlesingId.generate(),
                år = År(2001),
                forespurtTidspunkt = Instant.now().minus(Duration.ofHours(4))
            )
        )
        println("innlesing: ${innleding.id}")
        val status = statusService.checkStatus()
        assertThat(status)
            .isInstanceOf(ApplicationStatus.Feil::class.java)
            .extracting("feil")
            .isEqualTo("Innlesing er ikke prosessert")
    }

    @Test
    @Order(3)
    @Disabled
    fun `mottakere ikke prosessert`() {
        val innlesing = innlesingRepository.bestilt(
            BarnetrygdInnlesing.Bestilt(
                id = InnlesingId.generate(),
                år = År(2001),
                forespurtTidspunkt = Instant.now().minus(Duration.ofHours(3))
            )
        )
        val startet = innlesingRepository.start(innlesing.startet(10))
        innlesingRepository.fullført(startet.ferdig())

        val barnetrygdmottaker = Barnetrygdmottaker.Transient(
            ident = Ident("12345123451"),
            correlationId = CorrelationId(UUID.randomUUID()),
            innlesingId = innlesing.id,
        )

        val mottatt = mottakerRepository.insert(barnetrygdmottaker)
        mottakerRepository.updateStatus(mottatt.ferdig())

        val antallFerdig = mottakerRepository.finnAntallMottakereMedStatusForInnlesing(
            Barnetrygdmottaker.Status.Ferdig::class,
            innlesing.id
        )

        val status = statusService.checkStatus()
        assertThat(status)
            .isInstanceOf(ApplicationStatus.Feil::class.java)
            .extracting("feil")
            .isEqualTo("Alle mottakere er ikke prosessert")
    }

    @Test
    @Order(4)
    fun `fersk innlesing der alle mottakere ikke er ferdige`() {
        val innlesingGammel = innlesingRepository.bestilt(
            BarnetrygdInnlesing.Bestilt(
                id = InnlesingId.generate(),
                år = År(2001),
                forespurtTidspunkt = Instant.now().minus(Duration.ofDays(5))
            )
        )

        val innlesingNy = innlesingRepository.bestilt(
            BarnetrygdInnlesing.Bestilt(
                id = InnlesingId.generate(),
                år = År(2001),
                forespurtTidspunkt = Instant.now().minus(Duration.ofMinutes(5))
            )
        )

        val barnetrygdmottaker = Barnetrygdmottaker.Transient(
            ident = Ident("12345123451"),
            correlationId = CorrelationId(UUID.randomUUID()),
            innlesingId = innlesingGammel.id, // tilhører ikke siste innlesing
        )

        val mottatt = mottakerRepository.insert(barnetrygdmottaker)
        val klar = mottakerRepository.find(mottatt.id)

        val status = statusService.checkStatus()
        assertThat(status)
            .isInstanceOf(ApplicationStatus.OK::class.java)
    }

    @Test
    @Order(5)
    fun `mottakere har feilet for en innlesing der prosesseringsfristen har passert`() {
        // en ny innlesing for ikke å trigge andre feil
        val innlesingGammel = innlesingRepository.bestilt(
            BarnetrygdInnlesing.Bestilt(
                id = InnlesingId.generate(),
                år = År(2001),
                forespurtTidspunkt = Instant.now().minus(Duration.ofDays(5))
            )
        )

        Thread.sleep(50)

        val innlesingNy = innlesingRepository.bestilt(
            BarnetrygdInnlesing.Bestilt(
                id = InnlesingId.generate(),
                år = År(2001),
                forespurtTidspunkt = Instant.now().minus(Duration.ofHours(5))
            )
        )

        val barnetrygdmottaker = Barnetrygdmottaker.Transient(
            ident = Ident("12345123451"),
            correlationId = CorrelationId(UUID.randomUUID()),
            innlesingId = innlesingGammel.id, // tilhører ikke siste innlesing
        )

        innlesingRepository.start(
            innlesingNy.startet(1)
        )

        val sisteInnlesing = innlesingRepository.finnSisteInnlesing()!!
        println("sisteInnlesing: ${sisteInnlesing.id}")
        innlesingRepository.fullført(
            sisteInnlesing.ferdig()
        )

        val mottatt = mottakerRepository.insert(barnetrygdmottaker)
        mottakerRepository.updateStatus(mottatt.retry("1"))
        mottakerRepository.updateStatus(mottatt.retry("2"))
        mottakerRepository.updateStatus(mottatt.retry("3"))
        mottakerRepository.updateStatus(mottatt.retry("feilet"))
        val feilet = mottakerRepository.find(mottatt.id)

        val status = statusService.checkStatus()
        assertThat(status)
            .isInstanceOf(ApplicationStatus.Feil::class.java)
            .extracting("feil")
            .isEqualTo("Alle mottakere er ikke prosessert")
    }

    @Test
    @Order(6)
    fun `mottakere er ikke prosessert for en innlesing der prosesseringsfristen har passert`() {
        // en ny innlesing for ikke å trigge andre feil
        val innlesingGammel = innlesingRepository.bestilt(
            BarnetrygdInnlesing.Bestilt(
                id = InnlesingId.generate(),
                år = År(2001),
                forespurtTidspunkt = Instant.now().minus(Duration.ofDays(5))
            )
        )

        Thread.sleep(50)

        val innlesingNy = innlesingRepository.bestilt(
            BarnetrygdInnlesing.Bestilt(
                id = InnlesingId.generate(),
                år = År(2001),
                forespurtTidspunkt = Instant.now().minus(Duration.ofHours(5))
            )
        )
        println("GAMMEL: ${innlesingGammel.id}")
        println("    NY: ${innlesingNy.id}")

        val barnetrygdmottaker = Barnetrygdmottaker.Transient(
            ident = Ident("12345123451"),
            correlationId = CorrelationId(UUID.randomUUID()),
            innlesingId = innlesingGammel.id, // tilhører ikke siste innlesing
        )
        innlesingRepository.start(
            innlesingNy.startet(1)
        )

        val finnSisteInnlesing = innlesingRepository.finnSisteInnlesing()
        println("finnSisteInnlesing: $finnSisteInnlesing")
        innlesingRepository.fullført(
            finnSisteInnlesing!!.ferdig()
        )

        val mottatt = mottakerRepository.insert(barnetrygdmottaker)
        val feilet = mottakerRepository.find(mottatt.id)

        val status = statusService.checkStatus()
        assertThat(status)
            .isInstanceOf(ApplicationStatus.Feil::class.java)
            .extracting("feil")
            .isEqualTo("Alle mottakere er ikke prosessert")
    }
}