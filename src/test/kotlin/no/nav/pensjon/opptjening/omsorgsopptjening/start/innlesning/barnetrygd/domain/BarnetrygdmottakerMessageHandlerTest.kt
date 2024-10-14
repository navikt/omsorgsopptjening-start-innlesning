package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.InnlesingRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdmottakerRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import java.time.Instant

class BarnetrygdmottakerMessageHandlerTest {

    private val innlesingRepository: InnlesingRepository = mock()
    private val barnetrygdmottakerRepository: BarnetrygdmottakerRepository = mock()
    private val handler: BarnetrygdmottakerMessageHandler = BarnetrygdmottakerMessageHandler(
        innlesingRepository,
        barnetrygdmottakerRepository
    )

    private val innlesingId = InnlesingId.generate()
    private val correlationId = CorrelationId.generate()

    private val startmelding = BarnetrygdmottakerMelding.Start(
        correlationId = correlationId,
        innlesingId = innlesingId,
        forventetAntallIdenter = 1,
    )

    private val datamelding = BarnetrygdmottakerMelding.Data(
        personIdent = "12345",
        correlationId = correlationId,
        innlesingId = innlesingId,
        forventetAntallIdenter = 1
    )

    private val sluttmelding = BarnetrygdmottakerMelding.Slutt(
        correlationId = correlationId,
        innlesingId = innlesingId,
        forventetAntallIdenter = 1
    )

    private val bestilt = BarnetrygdInnlesing.Bestilt(
        id = innlesingId,
        år = 6624,
        forespurtTidspunkt = Instant.now(),
    )

    private val startet = BarnetrygdInnlesing.Startet(
        id = innlesingId,
        år = 6624,
        forespurtTidspunkt = Instant.now(),
        startTidspunkt = Instant.now(),
        antallIdenterLest = 1,
    )

    private val ferdig = BarnetrygdInnlesing.Ferdig(
        id = innlesingId,
        år = 6624,
        forespurtTidspunkt = Instant.now(),
        startTidspunkt = Instant.now(),
        ferdigTidspunkt = Instant.now(),
        antallIdenterLest = 1,
        forventetAntallIdentiteter = 1,
    )


    @Test
    fun `kaster exception dersom innlesingen ikke eksisterer`() {
        given(innlesingRepository.finn(any())).willReturn(null)
        assertThrows<BarnetrygdInnlesingException.EksistererIkke> {
            handler.handle(listOf(startmelding))
        }
    }

    @Test
    fun `gitt at status på innlesingen er startet skal man kunne håndtere en datamelding`() {
        given(innlesingRepository.finn(any())).willReturn(startet)
        handler.handle(listOf(datamelding))
        verify(innlesingRepository, times(3)).finn(any())
        verify(barnetrygdmottakerRepository).insertBatch(any())
    }

    @Test
    fun `gitt at status på innlesingen er startet skal man kunne håndtere en sluttmelding`() {
        given(innlesingRepository.finn(any())).willReturn(startet)
        handler.handle(listOf(sluttmelding))
        verify(innlesingRepository, times(3)).finn(any())
        verify(innlesingRepository).fullført(any())
    }

    @Test
    fun `gitt at status på innlesingen er avsluttet kastest det exception dersom man forsøker håndtere data igjen`() {
        given(innlesingRepository.finn(any())).willReturn(ferdig)
        assertThrows<BarnetrygdInnlesingException.UgyldigTistand> {
            handler.handle(listOf(datamelding))
        }
        verify(innlesingRepository, times(2)).finn(any())
        verifyNoInteractions(barnetrygdmottakerRepository)
    }

    @Test
    fun `gitt at status på innlesingen er avsluttet kastest det exception dersom man forsøker å avslutte igjen`() {
        given(innlesingRepository.finn(any())).willReturn(ferdig)
        assertThrows<BarnetrygdInnlesingException.UgyldigTistand> {
            handler.handle(listOf(sluttmelding))
        }
        verify(innlesingRepository, times(3)).finn(any())
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
                forventetAntallIdentiteter = 2,
            )
        )

        assertThrows<BarnetrygdInnlesingException.UgyldigTistand> {
            handler.handle(listOf(sluttmelding))
        }
        verify(innlesingRepository, times(3)).finn(any())
        verifyNoInteractions(barnetrygdmottakerRepository)
    }

    @Test
    fun `kan håndtere alle meldinger fra samme innlesing samtidig`() {
        given(innlesingRepository.finn(any()))
            .willReturn(
                BarnetrygdInnlesing.Bestilt(
                    id = innlesingId,
                    år = 6624,
                    forespurtTidspunkt = Instant.now(),
                )
            )
            .willReturn(
                BarnetrygdInnlesing.Startet(
                    id = innlesingId,
                    år = 6624,
                    forespurtTidspunkt = Instant.now(),
                    startTidspunkt = Instant.now(),
                    antallIdenterLest = 1,
                )
            )

        handler.handle(listOf(startmelding, datamelding, sluttmelding))
        verify(innlesingRepository, times(3)).finn(any())
    }

    @Test
    fun `kan håndtere alle meldinger fra flere forskjellige innlesinger samtidig`() {
        val enannen = InnlesingId.generate()
        given(innlesingRepository.finn(innlesingId.toString()))
            .willReturn(bestilt)
            .willReturn(startet)

        given(innlesingRepository.finn(enannen.toString()))
            .willReturn(bestilt.copy(id = enannen))
            .willReturn(startet.copy(id = enannen))

        handler.handle(
            listOf(
                startmelding,
                datamelding,
                sluttmelding,
                startmelding.copy(innlesingId = enannen),
                datamelding.copy(innlesingId = enannen),
                sluttmelding.copy(innlesingId = enannen)
            )
        )
        verify(innlesingRepository, times(3)).finn(innlesingId.toString())
        verify(innlesingRepository, times(3)).finn(enannen.toString())
        verify(barnetrygdmottakerRepository).insertBatch(
            listOf(
                Barnetrygdmottaker.Transient(
                    ident = "12345",
                    correlationId = correlationId,
                    innlesingId = innlesingId
                )
            )
        )
        verify(barnetrygdmottakerRepository).insertBatch(
            listOf(
                Barnetrygdmottaker.Transient(
                    ident = "12345",
                    correlationId = correlationId,
                    innlesingId = enannen
                )
            )
        )
    }
}