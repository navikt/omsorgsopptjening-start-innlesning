package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Rådata
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Topics
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.PersongrunnlagMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.serialize
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Mdc
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.BarnetrygdClient
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdInnlesingRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdmottakerRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.hjelpestønad.domain.HjelpestønadService
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.KafkaException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

@Service
class BarnetrygdmottakerService(
    private val client: BarnetrygdClient,
    private val barnetrygdmottakerRepository: BarnetrygdmottakerRepository,
    private val kafkaProducer: KafkaTemplate<String, String>,
    private val transactionTemplate: TransactionTemplate,
    private val hjelpestønadService: HjelpestønadService,
    private val barnetrygdInnlesingRepository: BarnetrygdInnlesingRepository,
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
                            try {
                                log.info("Start prosessering")
                                transactionTemplate.execute {
                                    barnetrygdmottaker.ferdig().also { barnetrygdmottaker ->

                                        val filter = GyldigÅrsintervallFilter(barnetrygdmottaker.år)

                                        val rådata = Rådata()

                                        val barnetrygdResponse = client.hentBarnetrygd(
                                            ident = barnetrygdmottaker.ident,
                                            filter = filter,
                                        ).also {
                                            rådata.leggTil(it.rådataFraKilde)
                                        }

                                        val hjelpestønad = barnetrygdResponse.barnetrygdsaker
                                            .associateBy { it.omsorgsyter }
                                            .mapValues { (_, persongrunnlag) ->
                                                val hjelpestønad = hjelpestønadService.hentHjelpestønad(
                                                    persongrunnlag = persongrunnlag,
                                                    filter = filter
                                                ).onEach { rådata.leggTil(it.second) }

                                                persongrunnlag.leggTilHjelpestønad(hjelpestønad.flatMap { it.first })
                                            }
                                            .map { it.value }

                                        kafkaProducer.send(
                                            createKafkaMessage(
                                                barnetrygdmottaker = barnetrygdmottaker,
                                                persongrunnlag = hjelpestønad,
                                                rådata = rådata,
                                            )
                                        ).get()

                                        barnetrygdmottakerRepository.updateStatus(barnetrygdmottaker)

                                        log.info("Melding prosessert")
                                    }
                                }
                            } catch (ex: Throwable) {
                                log.error("Fikk feil ved prosessering av melding", ex)
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
                                    log.error("Feil ved oppdatering av status til retry", ex)
                                    null
                                }
                            } finally {
                                log.info("Slutt prosessering")
                            }
                        }
                    }
                }
            return barnetrygdmottaker?.filterNotNull()?.ifEmpty { null }
        } finally {
            låsteTilBehandling?.let { barnetrygdmottakerRepository.frigi(it) }
        }
    }

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
}