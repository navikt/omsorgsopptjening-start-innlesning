package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Innlesing
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.InnlesingRepository
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BarnetrygdmottakerRepositoryTest : SpringContextTest.NoKafka() {

    @Autowired
    private lateinit var innlesingRepository: InnlesingRepository

    @Autowired
    private lateinit var barnetrygdmottakerRepository: BarnetrygdmottakerRepository

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @Test
    fun `finner ingen meldinger som skal prosesseres før alle meldingene i forsendelsen er lest inn`() {
        val innlesing = innlesingRepository.bestilt(Innlesing(id = InnlesingId.generate(), år = 2023))

        barnetrygdmottakerRepository.save(
            melding = Barnetrygdmottaker(
                ident = "123",
                correlationId = CorrelationId.generate(),
                innlesingId = innlesing.id
            )
        )

        assertNull(barnetrygdmottakerRepository.finnNesteUprosesserte())

        innlesingRepository.fullført(innlesing.id.toString())

        assertNotNull(barnetrygdmottakerRepository.finnNesteUprosesserte())
    }

    @Test
    fun `finnNesteUprosesserte låser raden slik at den ikke plukkes opp av andre connections`() {
        val innlesing = innlesingRepository.bestilt(Innlesing(
            id = InnlesingId.generate(),
            år = 2023
        )).also { innlesingRepository.fullført(it.id.toString()) }

        barnetrygdmottakerRepository.save(
            melding = Barnetrygdmottaker(
                ident = "123",
                correlationId = CorrelationId.generate(),
                innlesingId = innlesing.id
            )
        )

        //krev ny transaksjon slik at det opprettes ny connection
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW)

        transactionTemplate.execute {
            //låser den aktuelle raden for denne transaksjonens varighet
            Assertions.assertNotNull(barnetrygdmottakerRepository.finnNesteUprosesserte())

            //opprett ny transaksjon mens den forrige fortsatt lever
            transactionTemplate.execute {
                //skal ikke finne noe siden raden er låst pga "select for update skip locked"
                Assertions.assertNull(barnetrygdmottakerRepository.finnNesteUprosesserte())
            }
            //fortsatt samme transaksjon
            Assertions.assertNotNull(barnetrygdmottakerRepository.finnNesteUprosesserte())
        } //rad ikke låst lenger ved transaksjon slutt


        //ny transaksjon finner raden da den ikke lenger er låst
        transactionTemplate.execute {
            Assertions.assertNotNull(barnetrygdmottakerRepository.finnNesteUprosesserte())
        }
    }
}