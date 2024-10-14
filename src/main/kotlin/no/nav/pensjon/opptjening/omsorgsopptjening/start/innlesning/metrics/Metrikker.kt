package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Barnetrygdmottaker
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.HentBarnetrygdResponse
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.hjelpestønad.HentHjelpestønadDBResponse
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.kafka.BarnetrygdmottakerKafkaMelding
import org.springframework.stereotype.Component
import java.util.*

@Component
class Metrikker(private val registry: MeterRegistry) {

    private val hentBarnetrygdTimer = registry.timer("hentbarnetrygd", "tidsbruk", "hentet")
    private val hentHjelpestonadTimer = registry.timer("henthjelpestonad", "tidsbruk", "hentet")
    private val hentPdlTimer = registry.timer("hentpdl", "tidsbruk", "hentet")

    private val antallFeiledeMeldinger = registry.counter("meldinger", "antall", "feilet")

    private val antallKlar = registry.counter("barnetrygdmottaker", "status", "klar")
    private val antallFerdig = registry.counter("barnetrygdmottaker", "status", "ferdig")
    private val antallRetry = registry.counter("barnetrygdmottaker", "status", "retry")
    private val antallFeilet = registry.counter("barnetrygdmottaker", "status", "feilet")

    // TODO: avsluttet og stoppet er nye statuser, og admin-grensesnittet må oppdateres til å ta høyde for de
    private val antallAvsluttet = registry.counter("barnetrygdmottaker", "status", "avsluttet")
    private val antallStoppet = registry.counter("barnetrygdmottaker", "status", "stoppet")
    private val timer = registry.timer("barnetrygdmottaker", "tidsbruk", "prosessert")

    fun kafkaMeldingstypeCounter(melding: BarnetrygdmottakerKafkaMelding): Counter {
        return registry.counter(
            "barnetrygdMelding",
            "status",
            melding.meldingstype.toString().lowercase(Locale.ENGLISH),
            "innlesingId",
            melding.requestId.toString(),
        )
    }

    fun tellBarnetrygdmottakerStatus(lambda: () -> List<Barnetrygdmottaker>?): List<Barnetrygdmottaker>? {
        return timer.recordCallable(lambda)?.onEach {
            when (it.status) {
                is Barnetrygdmottaker.Status.Feilet -> antallFeilet.increment()
                is Barnetrygdmottaker.Status.Ferdig -> antallFerdig.increment()
                is Barnetrygdmottaker.Status.Klar -> antallKlar.increment()
                is Barnetrygdmottaker.Status.Retry -> antallRetry.increment()
                is Barnetrygdmottaker.Status.Avsluttet -> antallAvsluttet.increment()
                is Barnetrygdmottaker.Status.Stoppet -> antallStoppet.increment()
            }
        }
    }

    fun tellKafkaMeldingstype(lambda: () -> List<BarnetrygdmottakerKafkaMelding>): List<BarnetrygdmottakerKafkaMelding> {
        return lambda().onEach {
            registry.counter("meldinger", "antall", "lest", "innlesingId", it.requestId.toString()).increment()
            kafkaMeldingstypeCounter(it).increment()
        }
    }

    fun målHentBarnetrygd(lambda: () -> HentBarnetrygdResponse?): HentBarnetrygdResponse? {
        return hentBarnetrygdTimer.recordCallable(lambda)!!
    }

    fun målHentHjelpestønad(lambda: () -> HentHjelpestønadDBResponse?): HentHjelpestønadDBResponse? {
        return hentHjelpestonadTimer.recordCallable(lambda)!!
    }

    fun målHentPdl(lambda: () -> HentHjelpestønadDBResponse?): HentHjelpestønadDBResponse? {
        return hentPdlTimer.recordCallable(lambda)!!
    }

    fun målFeiletMelding(lambda: () -> Unit) {
        antallFeiledeMeldinger.increment()
    }
}