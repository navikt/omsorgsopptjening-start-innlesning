package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.kafka

import java.time.Instant
import java.util.UUID
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.SpringContextTest
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdInnlesing
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdInnlesingException
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdmottakerMessageHandler
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.År
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.kafka.BarnetrygdmottakerKafkaListener
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.kafka.BarnetrygdmottakerKafkaMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.kafka.BarnetrygdmottakerKafkaTopic
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.kafka.InnlesingInvalidatingRetryListener
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.kafka.InvalidateOnExceptionWrapper
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.kafka.KafkaMeldingDeserialiseringException
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.InnlesingRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.metrics.Metrikker
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.IncorrectUpdateSemanticsDataAccessException
import org.springframework.kafka.support.Acknowledgment
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils

class BarnetrygdmottakerKafkaListenerTest {

    @Nested
    inner class ExceptionHandlingTest : SpringContextTest.NoKafka() {

        @Autowired
        private lateinit var metrikker: Metrikker

        private val handler: BarnetrygdmottakerMessageHandler = mock()
        private val ack: Acknowledgment = mock()
        private val gyldigRecord: ConsumerRecord<String, String> = ConsumerRecord<String, String>(
            BarnetrygdmottakerKafkaTopic.NAME,
            0,
            0,
            null,
            """
                    {
                        "meldingstype":"DATA",
                        "requestId":"17de91e9-b01a-4d95-84bc-80630ded678e",
                        "personident":"12345"
                    }
                """.trimIndent()
        )
        private val ugyldigRecord: ConsumerRecord<String, String> = ConsumerRecord<String, String>(
            BarnetrygdmottakerKafkaTopic.NAME,
            0,
            0,
            null,
            """
                    {
                        "abc":"123"
                    }
                """.trimIndent()
        )

        @Test
        fun `gitt at innlesingen ikke eksisterer kastes det en ny exception`() {
            val listener = BarnetrygdmottakerKafkaListener(handler, metrikker)
            whenever(handler.handle(any())).thenThrow(BarnetrygdInnlesingException.EksistererIkke("17de91e9-b01a-4d95-84bc-80630ded678e"))
            assertThrows<BarnetrygdInnlesingException.EksistererIkke> {
                listener.poll(
                    listOf(gyldigRecord),
                    ack
                )
            }
            verify(handler).handle(any())
            verifyNoInteractions(ack)
        }

        @Test
        fun `gitt at vi mottar en melding med ukjent dataformat kastes en ny exception`() {
            val listener = BarnetrygdmottakerKafkaListener(handler, metrikker)

            assertThrows<KafkaMeldingDeserialiseringException> {
                listener.poll(
                    listOf(ugyldigRecord),
                    ack
                )
            }
            verifyNoInteractions(handler)
            verifyNoInteractions(ack)
        }

        @Test
        fun `gitt at innlesingen er i ugyldig tilstand kastes en ny exception som signaliserer at innlesingen skal invalideres`() {
            val listener = BarnetrygdmottakerKafkaListener(handler, metrikker)

            whenever(handler.handle(any())).thenThrow(
                BarnetrygdInnlesingException.UgyldigTistand(
                    "17de91e9-b01a-4d95-84bc-80630ded678e",
                    "data"
                )
            )
            assertThrows<InvalidateOnExceptionWrapper> {
                listener.poll(
                    listOf(gyldigRecord),
                    ack
                )
            }
            verify(handler).handle(any())
            verifyNoInteractions(ack)
        }

        @Test
        fun `gitt at det oppstår en ukjent feil ved prosessering av innlesingen kastes en ny exception som signaliserer at innlesingen skal invalideres`() {
            val listener = BarnetrygdmottakerKafkaListener(handler, metrikker)

            whenever(handler.handle(any())).thenThrow(
                IncorrectUpdateSemanticsDataAccessException("something weird with the db")
            )
            assertThrows<InvalidateOnExceptionWrapper> {
                listener.poll(
                    listOf(gyldigRecord),
                    ack
                )
            }
            verify(handler).handle(any())
            verifyNoInteractions(ack)
        }
    }

    @Nested
    inner class KafkaRetryHandlingTest : SpringContextTest.WithKafka() {
        @MockitoBean
        private lateinit var handler: BarnetrygdmottakerMessageHandler

        @MockitoBean
        private lateinit var retryListener: InnlesingInvalidatingRetryListener

        @MockitoBean
        private lateinit var innlesingRepository: InnlesingRepository

        @Test
        fun `gitt at meldingsformatet er ukjent, skal det ikke gjøres noe forsøk på retry`() {
            val captor = argumentCaptor<Exception> {
                doNothing().whenever(
                    retryListener
                ).failedDelivery(
                    any<ConsumerRecords<String, String>>(),
                    capture(),
                    any()
                )
            }
            sendUgyldigMeldingKafka()

            verify(retryListener, timeout(10_000)).recovered(any<ConsumerRecords<String, String>>(), any())
            verify(retryListener, timeout(10_000)).failedDelivery(any<ConsumerRecords<String, String>>(), any(), any())
            assertInstanceOf(KafkaMeldingDeserialiseringException::class.java, captor.allValues.single().cause)
            verifyNoMoreInteractions(retryListener)
        }

        @Test
        fun `gitt innlesingen ikke eksisterer skal det ikke gjøres noe forsøk på retry`() {
            given(handler.handle(any())).willThrow(BarnetrygdInnlesingException.EksistererIkke("17de91e9-b01a-4d95-84bc-80630ded678e"))
            val captor = argumentCaptor<Exception> {
                doNothing().whenever(
                    retryListener
                ).failedDelivery(
                    any<ConsumerRecords<String, String>>(),
                    capture(),
                    any()
                )
            }
            sendStartInnlesingKafka("17de91e9-b01a-4d95-84bc-80630ded678e")

            verify(retryListener, timeout(10_000)).recovered(any<ConsumerRecords<String, String>>(), any())
            verify(retryListener, timeout(10_000)).failedDelivery(any<ConsumerRecords<String, String>>(), any(), any())
            assertInstanceOf(BarnetrygdInnlesingException.EksistererIkke::class.java, captor.allValues.single().cause)
            verifyNoMoreInteractions(retryListener)
        }

        @Test
        fun `gitt innlesingen er i ugyldig tilstand skal det ikke gjøres noe forsøk på retry`() {
            given(handler.handle(any())).willThrow(
                BarnetrygdInnlesingException.UgyldigTistand(
                    "17de91e9-b01a-4d95-84bc-80630ded678e",
                    "STOPP"
                )
            )
            val captor = argumentCaptor<Exception> {
                doNothing().whenever(
                    retryListener
                ).failedDelivery(
                    any<ConsumerRecords<String, String>>(),
                    capture(),
                    any()
                )
            }
            sendStartInnlesingKafka("17de91e9-b01a-4d95-84bc-80630ded678e")

            verify(retryListener, timeout(10_000)).recovered(any<ConsumerRecords<String, String>>(), any())
            verify(retryListener, timeout(10_000)).failedDelivery(any<ConsumerRecords<String, String>>(), any(), any())
            assertInstanceOf(InvalidateOnExceptionWrapper::class.java, captor.allValues.single().cause).also {
                assertInstanceOf(BarnetrygdInnlesingException.UgyldigTistand::class.java, it.cause)
            }
            verifyNoMoreInteractions(retryListener)
        }

        @Test
        fun `gitt at det kastes en ukjent feil ved prosessering skal meldingen forsøkes på nytt x antall ganger med y tids mellomrom`() {
            given(handler.handle(any())).willThrow(IncorrectUpdateSemanticsDataAccessException("something weird with the db"))
            val captor = argumentCaptor<Exception> {
                doNothing().whenever(
                    retryListener
                ).failedDelivery(
                    any<ConsumerRecords<String, String>>(),
                    capture(),
                    any()
                )
            }
            sendStartInnlesingKafka("17de91e9-b01a-4d95-84bc-80630ded678e")

            verify(retryListener, timeout(10_000).times(1)).recovered(any<ConsumerRecords<String, String>>(), any())
            verify(retryListener, timeout(10_000).times(3)).failedDelivery(any<ConsumerRecords<String, String>>(), any(), any())
            assertInstanceOf(InvalidateOnExceptionWrapper::class.java, captor.lastValue.cause).also {
                assertInstanceOf(IncorrectUpdateSemanticsDataAccessException::class.java, it.cause)
            }
            verifyNoMoreInteractions(retryListener)
        }

        @Test
        fun `gitt at det kastes en ukjent feil ved prosessering skal meldingen forsøkes på nytt x antall ganger med y tids mellomrom og invalideres hvis den eksisterer`() {
            given(handler.handle(any())).willThrow(IncorrectUpdateSemanticsDataAccessException("something weird with the db"))
            given(retryListener.recovered(any<ConsumerRecords<String, String>>(), any())).willCallRealMethod()
            given(innlesingRepository.finn(any())).willReturn(
                BarnetrygdInnlesing.Startet(
                    id = InnlesingId.fromString("17de91e9-b01a-4d95-84bc-80630ded678e"),
                    år = År(2184),
                    forespurtTidspunkt = Instant.now(),
                    startTidspunkt = Instant.now(),
                    antallIdenterLest = 1,
                    forventetAntallIdentiteter = 1

                )
            )
            ReflectionTestUtils.setField(retryListener, "innlesingRepository", innlesingRepository)
            ReflectionTestUtils.setField(retryListener, "invalidated", mutableListOf<String>())

            sendStartInnlesingKafka("17de91e9-b01a-4d95-84bc-80630ded678e")

            verify(innlesingRepository, timeout(10_000)).invalider(UUID.fromString("17de91e9-b01a-4d95-84bc-80630ded678e"))
            verify(retryListener, timeout(10_000).times(3)).failedDelivery(any<ConsumerRecords<String, String>>(), any(), any())
            verify(retryListener, timeout(10_000).times(1)).recovered(any<ConsumerRecords<String, String>>(), any())
            verifyNoMoreInteractions(retryListener)
        }

        @Test
        fun `gitt at det kastes en ukjent feil ved proessering skal meldingen forsøkes på nytt x antall ganger med y tids mellomrom, hvis den er ferdig skal den ikke invalideres`() {
            given(handler.handle(any())).willThrow(IncorrectUpdateSemanticsDataAccessException("something weird with the db"))
            given(retryListener.recovered(any<ConsumerRecords<String, String>>(), any())).willCallRealMethod()
            given(innlesingRepository.finn(any())).willReturn(
                BarnetrygdInnlesing.Ferdig(
                    id = InnlesingId.fromString("17de91e9-b01a-4d95-84bc-80630ded678e"),
                    år = År(2184),
                    forespurtTidspunkt = Instant.now(),
                    startTidspunkt = Instant.now(),
                    antallIdenterLest = 1,
                    forventetAntallIdentiteter = 1,
                    ferdigTidspunkt = Instant.now()

                )
            )
            ReflectionTestUtils.setField(retryListener, "innlesingRepository", innlesingRepository)
            ReflectionTestUtils.setField(retryListener, "invalidated", mutableListOf<String>())

            sendStartInnlesingKafka("17de91e9-b01a-4d95-84bc-80630ded678e")

            verify(retryListener, timeout(10_000).times(1)).recovered(any<ConsumerRecords<String, String>>(), any())
            verify(retryListener, timeout(10_000).times(3)).failedDelivery(any<ConsumerRecords<String, String>>(), any(), any())
            verify(innlesingRepository, never()).invalider(UUID.fromString("17de91e9-b01a-4d95-84bc-80630ded678e"))
            verifyNoMoreInteractions(retryListener)
        }
    }

    @Nested
    inner class KafkaIntegrationTest : SpringContextTest.WithKafka() {
        @Autowired
        private lateinit var innlesingRepository: InnlesingRepository

        @Test
        fun `happy path`() {
            val innlesing = innlesingRepository.bestilt(
                BarnetrygdInnlesing.Bestilt(
                    id = InnlesingId.generate(),
                    År(2020),
                    forespurtTidspunkt = Instant.now()
                )
            )

            sendStartInnlesingKafka(innlesing.id.toString())
            sendBarnetrygdmottakerDataKafka(
                melding = BarnetrygdmottakerKafkaMelding(
                    meldingstype = BarnetrygdmottakerKafkaMelding.Type.DATA,
                    requestId = UUID.fromString(innlesing.id.toString()),
                    personident = "12345678910",
                    antallIdenterTotalt = 1
                )
            )
            sendSluttInnlesingKafka(innlesing.id.toString())

            await { innlesingRepository.finn(innlesing.id.toString()) is BarnetrygdInnlesing.Ferdig }

            innlesingRepository.finn(innlesing.id.toString())!!.also { barnetrygdInnlesing ->
                assertInstanceOf(BarnetrygdInnlesing.Ferdig::class.java, barnetrygdInnlesing).also {
                    assertThat(it.id).isEqualTo(innlesing.id)
                    assertThat(innlesing.år).isEqualTo(År(2020))
                    assertThat(it.forespurtTidspunkt).isNotNull()
                    assertThat(it.startTidspunkt).isNotNull()
                    assertThat(it.ferdigTidspunkt).isNotNull()
                }
            }
        }

        @Test
        fun `invalidering av innlesing`() {
            val innlesing = innlesingRepository.bestilt(
                BarnetrygdInnlesing.Bestilt(
                    id = InnlesingId.generate(),
                    år = År(2020),
                    forespurtTidspunkt = Instant.now()
                )
            )

            sendStartInnlesingKafka(innlesing.id.toString())
            // Vent til første START faktisk er konsumert (Bestilt -> Startet); rada finst alt som Bestilt,
            // så ei blank != null-sjekk ville passert med ein gong og racet den andre START-meldinga.
            await { innlesingRepository.finn(innlesing.id.toString()) is BarnetrygdInnlesing.Startet }

            sendStartInnlesingKafka(innlesing.id.toString())
            await { innlesingRepository.finn(innlesing.id.toString()) == null }
        }
    }
}