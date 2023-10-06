package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdInnlesingRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdmottakerRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import java.time.Instant

class BarnetrygdmottakerMessageHandlerTest {

    private val innlesingRepository: BarnetrygdInnlesingRepository = mock()
    private val barnetrygdmottakerRepository: BarnetrygdmottakerRepository = mock()
    private val handler: BarnetrygdmottakerMessageHandler = BarnetrygdmottakerMessageHandler(
        innlesingRepository,
        barnetrygdmottakerRepository
    )

    private val startmelding = BarnetrygdmottakerMelding.Start(
        correlationId = CorrelationId.generate(),
        innlesingId = InnlesingId.generate(),
        forventetAntallIdenter = 1,
    )

    private val datamelding = BarnetrygdmottakerMelding.Data(
        personIdent = "12345",
        correlationId = CorrelationId.generate(),
        innlesingId = InnlesingId.generate(),
        forventetAntallIdenter = 1
    )

    private val sluttmelding = BarnetrygdmottakerMelding.Slutt(
        correlationId = CorrelationId.generate(),
        innlesingId = InnlesingId.generate(),
        forventetAntallIdenter = 1
    )

    @Test
    fun `kaster exception dersom innlesingen ikke eksisterer`() {
        given(innlesingRepository.finn(any())).willReturn(null)
        assertThrows<BarnetrygdInnlesingException.EksistererIkke> {
            handler.handle(startmelding)
        }
    }


    @Test
    fun `gitt at status på innlesingen er startet skal man kunne håndtere en datamelding`() {
        given(innlesingRepository.finn(any())).willReturn(
            BarnetrygdInnlesing.Startet(
                id = InnlesingId.generate(),
                år = 6624,
                forespurtTidspunkt = Instant.now(),
                startTidspunkt = Instant.now(),
                antallIdenterLest = 1,
            )
        )
        handler.handle(datamelding)
        verify(innlesingRepository).finn(any())
        verify(barnetrygdmottakerRepository).insert(any())
    }

    @Test
    fun `gitt at status på innlesingen er startet skal man kunne håndtere en sluttmelding`() {
        given(innlesingRepository.finn(any())).willReturn(
            BarnetrygdInnlesing.Startet(
                id = InnlesingId.generate(),
                år = 6624,
                forespurtTidspunkt = Instant.now(),
                startTidspunkt = Instant.now(),
                antallIdenterLest = 1,
            )
        )
        handler.handle(sluttmelding)
        verify(innlesingRepository).finn(any())
        verify(innlesingRepository).fullført(any())
    }

    @Test
    fun `gitt at status på innlesingen er avsluttet kastest det exception dersom man forsøker håndtere data igjen`() {
        given(innlesingRepository.finn(any())).willReturn(
            BarnetrygdInnlesing.Ferdig(
                id = InnlesingId.generate(),
                år = 6624,
                forespurtTidspunkt = Instant.now(),
                startTidspunkt = Instant.now(),
                ferdigTidspunkt = Instant.now(),
                antallIdenterLest = 1,
            )
        )
        assertThrows<BarnetrygdInnlesingException.UgyldigTistand> {
            handler.handle(datamelding)
        }
        verify(innlesingRepository).finn(any())
        verifyNoInteractions(barnetrygdmottakerRepository)
    }

    @Test
    fun `gitt at status på innlesingen er avsluttet kastest det exception dersom man forsøker å avslutte igjen`() {
        given(innlesingRepository.finn(any())).willReturn(
            BarnetrygdInnlesing.Ferdig(
                id = InnlesingId.generate(),
                år = 6624,
                forespurtTidspunkt = Instant.now(),
                startTidspunkt = Instant.now(),
                ferdigTidspunkt = Instant.now(),
                antallIdenterLest = 1
            )
        )
        assertThrows<BarnetrygdInnlesingException.UgyldigTistand> {
            handler.handle(sluttmelding)
        }
        verify(innlesingRepository).finn(any())
        verifyNoInteractions(barnetrygdmottakerRepository)
    }

    @Test
    fun `gitt at forventet antall identer ikke stemmer overens med antall leste identer kastes exception`() {
        given(innlesingRepository.finn(any())).willReturn(
            BarnetrygdInnlesing.Ferdig(
                id = InnlesingId.generate(),
                år = 6624,
                forespurtTidspunkt = Instant.now(),
                startTidspunkt = Instant.now(),
                ferdigTidspunkt = Instant.now(),
                antallIdenterLest = 2,
            )
        )

        assertThrows<BarnetrygdInnlesingException.UgyldigTistand> {
            handler.handle(sluttmelding)
        }
        verify(innlesingRepository).finn(any())
        verifyNoInteractions(barnetrygdmottakerRepository)
    }
}