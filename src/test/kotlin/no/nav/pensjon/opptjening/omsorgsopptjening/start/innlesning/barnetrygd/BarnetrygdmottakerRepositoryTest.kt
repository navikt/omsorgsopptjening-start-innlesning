package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Innlesing
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.InnlesingRepo
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BarnetrygdmottakerRepositoryTest : SpringContextTest.NoKafka() {

    @Autowired
    private lateinit var innlesingRepo: InnlesingRepo

    @Autowired
    private lateinit var barnetrygdmottakerRepository: BarnetrygdmottakerRepository

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @Test
    fun `finner ingen meldinger som skal prosesseres før alle meldingene i forsendelsen er lest inn`() {
        val innlesing = innlesingRepo.forespurt(Innlesing(id = UUID.randomUUID().toString(), år = 2023))

        barnetrygdmottakerRepository.save(
            melding = Barnetrygdmottaker(
                ident = "123",
                correlationId = UUID.randomUUID(),
                requestId = innlesing.id
            )
        )

        assertNull(barnetrygdmottakerRepository.finnNesteUprosesserte())

        innlesingRepo.fullført(innlesing.id)

        assertNotNull(barnetrygdmottakerRepository.finnNesteUprosesserte())
    }

    @Test
    fun `finnNesteUprosesserte låser raden slik at den ikke plukkes opp av andre connections`() {
        val innlesing = innlesingRepo.forespurt(Innlesing(
            id = UUID.randomUUID().toString(),
            år = 2023
        )).also { innlesingRepo.fullført(it.id) }

        barnetrygdmottakerRepository.save(
            melding = Barnetrygdmottaker(
                ident = "123",
                correlationId = UUID.randomUUID(),
                requestId = innlesing.id
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