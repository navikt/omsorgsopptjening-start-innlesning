package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.monitorering

import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdInnlesing
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Barnetrygdmottaker.KortStatus
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdInnlesingRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdmottakerRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant.now
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

@Service
class StatusService(
    private val repo: BarnetrygdInnlesingRepository,
    private val mottakerRepo: BarnetrygdmottakerRepository,
) {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
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
            return ApplicationStatus.OK
        }
    }

    private fun finnAntallFerdigeMottakere(innlesing: BarnetrygdInnlesing): Long =
        mottakerRepo.finnAntallMottakereMedStatusForInnlesing(KortStatus.FERDIG, innlesing.id)

    private fun ikkeProsessert(innlesing: BarnetrygdInnlesing): Boolean {
        return innlesing !is BarnetrygdInnlesing.Ferdig
    }

    private fun forGammel(innlesing: BarnetrygdInnlesing): Boolean {
        val minimumTidspunkt = now().minus(400.days.toJavaDuration())
        return innlesing.forespurtTidspunkt < minimumTidspunkt
    }

    private fun mottakereIkkeProsessert(innlesing: BarnetrygdInnlesing, antallFerdigeMottakere: Long): Boolean {
        val forventetAntallIdentiteter = innlesing.forventetAntallIdentiteter
        if (forventetAntallIdentiteter == null) {
            log.error("Innlesing ${innlesing.id} mangler forventet antall mottakere")
            return true
        } else {
            return forventetAntallIdentiteter.toLong() != antallFerdigeMottakere
        }
    }

    private fun getBurdeVærtProsessert(sisteInnlesing: BarnetrygdInnlesing) =
        sisteInnlesing.forespurtTidspunkt < now().minus(2.hours.toJavaDuration())

}

sealed class ApplicationStatus {
    object OK : ApplicationStatus()
    object IkkeKjort : ApplicationStatus()
    class Feil(val feil: String) : ApplicationStatus()
}