package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Rådata
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.RådataFraKilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Topics
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.PersongrunnlagMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.serialize
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Mdc
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.BarnetrygdClient
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.HentBarnetrygdResponse
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdInnlesingRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdmottakerRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.hjelpestønad.domain.HjelpestønadService
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.person.external.pdl.PdlService
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.lang.reflect.UndeclaredThrowableException
import java.time.Instant
import java.util.*

@Service
class BarnetrygdmottakerService(
    private val client: BarnetrygdClient,
    private val barnetrygdmottakerRepository: BarnetrygdmottakerRepository,
    private val kafkaProducer: KafkaTemplate<String, String>,
    private val transactionTemplate: TransactionTemplate,
    private val hjelpestønadService: HjelpestønadService,
    private val barnetrygdInnlesingRepository: BarnetrygdInnlesingRepository,
    private val pdlService: PdlService,
    @Value("\${OMSORGSOPPTJENING_TOPIC}") val omsorgsopptjeningTopic: String
) {
    companion object {
        private val log = LoggerFactory.getLogger(BarnetrygdmottakerService::class.java)
    }

    fun bestillPersonerMedBarnetrygd(ar: Int): BarnetrygdInnlesing.Bestilt {
        return client.bestillBarnetrygdmottakere(ar).let { response ->
            BarnetrygdInnlesing.Bestilt(
                id = response.innlesingId,
                år = response.år,
                forespurtTidspunkt = Instant.now()
            ).also {
                barnetrygdInnlesingRepository.bestilt(it)
            }
        }
    }

    fun process(): List<Barnetrygdmottaker>? {
        return barnetrygdInnlesingRepository.finnAlleFullførte().stream()
            .map { processForInnlesingId(it) }
            .filter { it != null }
            .findFirst()
            .orElse(null)
    }

    fun processForInnlesingId(innlesingId: InnlesingId): List<Barnetrygdmottaker>? {
        val låsteTilBehandling = transactionTemplate.execute {
            barnetrygdmottakerRepository.finnNesteTilBehandling(innlesingId, 10)
        }
        try {
            val barnetrygdmottaker: List<Barnetrygdmottaker.Mottatt?>? =
                låsteTilBehandling?.data?.map { barnetrygdmottaker ->
                    Mdc.scopedMdc(barnetrygdmottaker.correlationId) {
                        Mdc.scopedMdc(barnetrygdmottaker.innlesingId) {
                            prosesserMottattBarnetrygmottaker(barnetrygdmottaker)
                        }
                    }
                }
            return barnetrygdmottaker?.filterNotNull()?.ifEmpty { null }
        } finally {
            låsteTilBehandling?.let { barnetrygdmottakerRepository.frigi(it) }
        }
    }

    private fun prosesserMottattBarnetrygmottaker(
        barnetrygdmottaker: Barnetrygdmottaker.Mottatt
    ): Barnetrygdmottaker.Mottatt? {
        return try {
            log.info("Start prosessering")
            transactionTemplate.execute {
                barnetrygdmottaker.ferdig().also { barnetrygdmottakerUtenPdlData ->

                    val filter = GyldigÅrsintervallFilter(barnetrygdmottakerUtenPdlData.år)

//                    val person = pdlService.hentPerson(barnetrygdmottaker.ident)
                    // temporary
                    val personId = PersonId(barnetrygdmottakerUtenPdlData.ident, setOf(barnetrygdmottakerUtenPdlData.ident))
                    println("%%% PERSON: $personId")

                    val barnetrygdmottaker = barnetrygdmottakerUtenPdlData.withPerson(personId)

                    val barnetrygdResponse = hentBarnetrygd(personId, filter)

                    val barnetrygdRådata = barnetrygdResponse.map { it.rådataFraKilde }

                    val persongrunnlag = barnetrygdResponse.map {
                        getPersongrunnlag(it)
                    }

                    val hjelpestønadGrunnlag = persongrunnlag.map {
                        hentHjelpestønadGrunnlag(it, filter)
                    }
                    val hjelpestønadPersongrunnlag = hjelpestønadGrunnlag.flatMap {
                        it.map { it.first }
                    }
                    val hjelpestønadRådata = hjelpestønadGrunnlag.flatMap {
                        it.flatMap { it.second }
                    }

                    val rådata = Rådata(barnetrygdRådata + hjelpestønadRådata)

                    kafkaProducer.send(
                        createKafkaMessage(
                            barnetrygdmottaker = barnetrygdmottaker,
                            persongrunnlag = hjelpestønadPersongrunnlag,
                            rådata = rådata,
                        )
                    ).get()

                    barnetrygdmottakerRepository.updatePersonIdent(barnetrygdmottaker)
                    barnetrygdmottakerRepository.updateStatus(barnetrygdmottaker)

                    log.info("Melding prosessert")
                }
            } // end transaction
        } catch (outerEx: Throwable) {
            val ex = when (outerEx) {
                is UndeclaredThrowableException -> outerEx.undeclaredThrowable
                else -> outerEx
            }
            log.warn("Fikk feil ved prosessering av melding: ${ex::class.qualifiedName}")

            try {
                transactionTemplate.execute {
                    barnetrygdmottaker.retry(ex.stackTraceToString()).let {
                        if (it.status is Barnetrygdmottaker.Status.Feilet) {
                            log.error("Gir opp videre prosessering av melding")
                        }
                        barnetrygdmottakerRepository.updateStatus(it)
                    }
                }
                null
            } catch (ex: Throwable) {
                log.error("Feil ved oppdatering av status til retry: ${ex::class.qualifiedName}")
                null
            }
        } finally {
            log.info("Slutt prosessering")
        }
    }

    private fun hentHjelpestønadGrunnlag(
        persongrunnlag: List<PersongrunnlagMelding.Persongrunnlag>,
        filter: GyldigÅrsintervallFilter
    ): List<Pair<PersongrunnlagMelding.Persongrunnlag, List<RådataFraKilde>>> {
        return persongrunnlag.map { persongrunnlagMedHjelpestønader(it, filter) }
    }

    private fun hentBarnetrygd(
//        barnetrygdmottaker: Barnetrygdmottaker.Mottatt,
        personId: PersonId,
        filter: GyldigÅrsintervallFilter
    ): List<HentBarnetrygdResponse> {
        return personId.historiske.map { fnr ->
            client.hentBarnetrygd(
                ident = fnr,
                filter = filter,
            )
        }
    }

    private fun persongrunnlagMedHjelpestønader(
        persongrunnlag: PersongrunnlagMelding.Persongrunnlag,
        filter: GyldigÅrsintervallFilter,
    ): Pair<PersongrunnlagMelding.Persongrunnlag, List<RådataFraKilde>> {
        val hjelpestønad = hjelpestønadService.hentHjelpestønad(
            // persongrunnlag = persongrunnlag,
            omsorgsmottakere = persongrunnlag.hentOmsorgsmottakere(),
            filter = filter
        )
        val hjelpestønadRådata = hjelpestønad.map { it.second }
        val hjelpestønadsperioder = hjelpestønad.flatMap { it.first }
        return Pair(persongrunnlag.medHjelpestønadPerioder(hjelpestønadsperioder), hjelpestønadRådata)
    }

    private fun getPersongrunnlag(barnetrygdResponse: HentBarnetrygdResponse) =
        barnetrygdResponse.barnetrygdsaker
            .groupBy { it.omsorgsyter }
            .map { it.value.single() }

    private fun createKafkaMessage(
        barnetrygdmottaker: Barnetrygdmottaker,
        persongrunnlag: List<PersongrunnlagMelding.Persongrunnlag>,
        rådata: Rådata
    ): ProducerRecord<String, String> {
        return ProducerRecord(
            omsorgsopptjeningTopic,
            null,
            serialize(
                Topics.Omsorgsopptjening.Key(
                    ident = barnetrygdmottaker.ident
                )
            ),
            serialize(
                PersongrunnlagMelding(
                    omsorgsyter = barnetrygdmottaker.ident,
                    persongrunnlag = persongrunnlag,
                    rådata = rådata,
                    innlesingId = barnetrygdmottaker.innlesingId,
                    correlationId = barnetrygdmottaker.correlationId,
                )
            )
        )
    }

    fun rekjørFeilede(innlesingId: UUID): Int {
        return barnetrygdmottakerRepository.oppdaterFeiledeRaderTilKlar(innlesingId)
    }

    fun stopp(id: UUID, melding: String): StoppResultat {
        return barnetrygdmottakerRepository.find(id)?.let { mottatt ->
            when (mottatt.status) {
                is Barnetrygdmottaker.Status.Feilet -> {
                    mottatt.stoppet(melding = melding).let {
                        barnetrygdmottakerRepository.updateStatus(it)
                        StoppResultat.STOPPET
                    }
                }

                is Barnetrygdmottaker.Status.Avsluttet -> StoppResultat.ALLEREDE_AVSLUTTET
                is Barnetrygdmottaker.Status.Ferdig -> StoppResultat.ALLEREDE_FERDIG
                is Barnetrygdmottaker.Status.Klar -> {
                    mottatt.stoppet(melding = melding).let {
                        barnetrygdmottakerRepository.updateStatus(it)
                        StoppResultat.STOPPET
                    }
                }

                is Barnetrygdmottaker.Status.Retry -> {
                    mottatt.stoppet(melding = melding).let {
                        barnetrygdmottakerRepository.updateStatus(it)
                        StoppResultat.STOPPET
                    }
                }

                is Barnetrygdmottaker.Status.Stoppet -> StoppResultat.ALLEREDE_STOPPET
            }
        } ?: StoppResultat.IKKE_FUNNET
    }

    fun avslutt(id: UUID, melding: String): AvsluttResultat {
        return barnetrygdmottakerRepository.find(id)?.let { mottatt ->
            when (mottatt.status) {
                is Barnetrygdmottaker.Status.Feilet -> {
                    mottatt.avsluttet(melding = melding).let {
                        barnetrygdmottakerRepository.updateStatus(it)
                        AvsluttResultat.AVSLUTTET
                    }
                }

                is Barnetrygdmottaker.Status.Avsluttet -> AvsluttResultat.ALLEREDE_AVSLUTTET
                is Barnetrygdmottaker.Status.Ferdig -> AvsluttResultat.ALLEREDE_FERDIG
                is Barnetrygdmottaker.Status.Klar -> {
                    mottatt.avsluttet(melding = melding).let {
                        barnetrygdmottakerRepository.updateStatus(it)
                        AvsluttResultat.AVSLUTTET
                    }
                }

                is Barnetrygdmottaker.Status.Retry -> {
                    mottatt.avsluttet(melding = melding).let {
                        barnetrygdmottakerRepository.updateStatus(it)
                        AvsluttResultat.AVSLUTTET
                    }
                }

                is Barnetrygdmottaker.Status.Stoppet -> {
                    mottatt.avsluttet(melding = melding).let {
                        barnetrygdmottakerRepository.updateStatus(it)
                        AvsluttResultat.AVSLUTTET
                    }
                }
            }
        } ?: AvsluttResultat.IKKE_FUNNET
    }

    fun restart(id: UUID): RestartResultat {
        fun restart(
            mottatt: Barnetrygdmottaker.Mottatt,
        ): RestartResultat {
            return mottatt.klar().let {
                barnetrygdmottakerRepository.updateStatus(it)
                RestartResultat.RESTARTET
            }
        }
        return barnetrygdmottakerRepository.find(id)?.let { mottatt ->
            when (mottatt.status) {
                is Barnetrygdmottaker.Status.Feilet -> restart(mottatt)
                is Barnetrygdmottaker.Status.Avsluttet -> restart(mottatt)
                is Barnetrygdmottaker.Status.Ferdig -> restart(mottatt)
                is Barnetrygdmottaker.Status.Klar -> RestartResultat.IKKE_NODVENDIG
                is Barnetrygdmottaker.Status.Retry -> restart(mottatt)
                is Barnetrygdmottaker.Status.Stoppet -> restart(mottatt)
            }
        } ?: RestartResultat.IKKE_FUNNET
    }


    enum class StoppResultat {
        ALLEREDE_STOPPET,
        ALLEREDE_AVSLUTTET,
        ALLEREDE_FERDIG,
        STOPPET,
        IKKE_FUNNET
    }

    enum class AvsluttResultat {
        ALLEREDE_AVSLUTTET,
        ALLEREDE_FERDIG,
        AVSLUTTET,
        IKKE_FUNNET,
    }

    enum class RestartResultat {
        RESTARTET,
        IKKE_NODVENDIG,
        IKKE_FUNNET,
    }
}
