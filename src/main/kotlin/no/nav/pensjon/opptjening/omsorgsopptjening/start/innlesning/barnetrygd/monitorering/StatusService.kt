package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.monitorering

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdInnlesing
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Barnetrygdmottaker
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.InnlesingRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdmottakerRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant.now
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

@Service
class StatusService(
    private val repo: InnlesingRepository,
    private val mottakerRepo: BarnetrygdmottakerRepository,
) {
    companion object {
        private val log = LoggerFactory.getLogger(StatusService::class.java)
    }

    fun checkStatus(): ApplicationStatus {
        val sisteInnlesing = repo.finnSisteInnlesing()
        if (sisteInnlesing == null) {
            return ApplicationStatus.IkkeKjort
        } else {
            val antallFerdigeMottakere = finnAntallFerdigeMottakere(sisteInnlesing)
            val burdeVærtProsessert = getBurdeVærtProsessert(sisteInnlesing)
            if (forGammel(sisteInnlesing)) return ApplicationStatus.Feil("For lenge siden siste innlesing")
            else if (burdeVærtProsessert && ikkeProsessert(sisteInnlesing)) return ApplicationStatus.Feil("Innlesing er ikke prosessert")
            else if (mottakereIkkeProsessert(
                    sisteInnlesing,
                    antallFerdigeMottakere
                )
            ) return ApplicationStatus.Feil("Alle mottakere er ikke prosessert")
            else if (harFeilededMottakere()) return ApplicationStatus.Feil("Det finnes feilede mottakere")
            return ApplicationStatus.OK
        }
    }

    private fun finnAntallFerdigeMottakere(innlesing: BarnetrygdInnlesing): Long {
        val ferdige =
            mottakerRepo.finnAntallMottakereMedStatusForInnlesing(Barnetrygdmottaker.Status.Ferdig::class, innlesing.id)
        val avsluttede = mottakerRepo.finnAntallMottakereMedStatusForInnlesing(
            Barnetrygdmottaker.Status.Avsluttet::class,
            innlesing.id
        )
        return ferdige + avsluttede

    }

    private fun ikkeProsessert(innlesing: BarnetrygdInnlesing): Boolean {
        return innlesing !is BarnetrygdInnlesing.Ferdig
    }

    private fun forGammel(innlesing: BarnetrygdInnlesing): Boolean {
        val minimumTidspunkt = now().minus(400.days.toJavaDuration())
        return innlesing.forespurtTidspunkt < minimumTidspunkt
    }

    private fun mottakereIkkeProsessert(innlesing: BarnetrygdInnlesing, antallFerdigeMottakere: Long): Boolean {
        val now = Clock.systemUTC().instant()
        val behandlingsperiode = 1.hours.toJavaDuration()
        val forventetAntallIdentiteter = innlesing.forventetAntallIdentiteter
        val gammelNokTilÅSjekke = innlesing.forespurtTidspunkt.plus(behandlingsperiode).isBefore(now)
        return if (!gammelNokTilÅSjekke) false
        else if (forventetAntallIdentiteter == null) {
            log.error("Innlesing ${innlesing.id} mangler forventet antall mottakere")
            true
        } else {
            forventetAntallIdentiteter.toLong() != antallFerdigeMottakere
        }
    }

    private fun harFeilededMottakere(): Boolean {
        return mottakerRepo.finnAntallMottakereMedStatus(Barnetrygdmottaker.Status.Feilet::class) > 0
    }

    private fun getBurdeVærtProsessert(sisteInnlesing: BarnetrygdInnlesing) =
        sisteInnlesing.forespurtTidspunkt < now().minus(2.hours.toJavaDuration())

}

sealed class ApplicationStatus {
    data object OK : ApplicationStatus()
    data object IkkeKjort : ApplicationStatus()
    class Feil(val feil: String) : ApplicationStatus()
}