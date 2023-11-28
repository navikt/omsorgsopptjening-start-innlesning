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
        val barnetrygdmottaker = transactionTemplate.execute {
            barnetrygdmottakerRepository.finnNesteTilBehandling(innlesingId, 10).map { barnetrygdmottaker ->
                Mdc.scopedMdc(barnetrygdmottaker.correlationId) {
                    Mdc.scopedMdc(barnetrygdmottaker.innlesingId) {
                        try {
                            log.info("Start prosessering")
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
                                        val hjelpestønad = hjelpestønadService.hentHjelpestønad(persongrunnlag)
                                            .onEach { rådata.leggTil(it.second) }

                                        persongrunnlag.copy(
                                            hjelpestønadsperioder = hjelpestønad.flatMap { it.first }
                                        )
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
                        } catch (ex: KafkaException) {
                            log.error("Fikk KafkaException ved prosessering av melding", ex)
                            throw ex
                        } catch (ex: SQLException) {
                            log.error("Fikk SQLException ved prosessering av melding", ex)
                            throw ex
                        } catch (ex: Throwable) {
                            log.error("Fikk feil ved prosessering av melding", ex)
                            barnetrygdmottaker.retry(ex.stackTraceToString()).let {
                                if (it.status is Barnetrygdmottaker.Status.Feilet) {
                                    log.error("Gir opp videre prosessering av melding")
                                }
                                barnetrygdmottakerRepository.updateStatus(it)
                            }
                            null
                        } finally {
                            log.info("Slutt prosessering")
                        }
                    }
                }
            }
        }
        // nonFatalException?.let { ex -> throw ex }
        return barnetrygdmottaker?.filterNotNull()?.ifEmpty { null }
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
}