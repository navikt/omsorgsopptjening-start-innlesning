package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

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
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

@Service
class BarnetrygdmottakerService(
    private val client: BarnetrygdClient,
    private val barnetrygdmottakerRepository: BarnetrygdmottakerRepository,
    private val kafkaProducer: KafkaTemplate<String, String>,
    private val transactionTemplate: TransactionTemplate,
    private val hjelpestønadService: HjelpestønadService,
    private val barnetrygdInnlesingRepository: BarnetrygdInnlesingRepository,
) {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
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

    fun process(): Barnetrygdmottaker? {
        return transactionTemplate.execute {
            barnetrygdmottakerRepository.finnNesteUprosesserte()?.let { barnetrygdmottaker ->
                Mdc.scopedMdc(barnetrygdmottaker.correlationId) {
                    Mdc.scopedMdc(barnetrygdmottaker.innlesingId) {
                        try {
                            transactionTemplate.execute {
                                log.info("Prosesserer barnetrygdmottaker med id:${barnetrygdmottaker.id}")
                                barnetrygdmottaker.ferdig().also { barnetrygdmottaker ->
                                    barnetrygdmottakerRepository.updateStatus(barnetrygdmottaker)

                                    val rådata = Rådata()

                                    val barnetrygdResponse = client.hentBarnetrygd(
                                        ident = barnetrygdmottaker.ident,
                                        ar = barnetrygdmottaker.år,
                                    ).also {
                                        rådata.leggTil(it.rådataFraKilde)
                                    }

                                    val hjelpestønad = barnetrygdResponse.barnetrygdsaker
                                        .associateBy { it.omsorgsyter }
                                        .mapValues { (_, persongrunnlag) ->
                                            val hjelpestønad =
                                                hjelpestønadService.hentHjelpestønad(persongrunnlag).also {
                                                    it.forEach { rådata.leggTil(it.second) }
                                                }

                                            persongrunnlag.copy(
                                                hjelpestønadsperioder = hjelpestønad.flatMap { it.first }
                                            )
                                        }
                                        .map { it.value }

                                    kafkaProducer.send(
                                        createKafkaMessage(
                                            barnetrygdmottaker = barnetrygdmottaker,
                                            persongrunnlag = hjelpestønad,
                                            rådata = rådata, //TODO legg til hjelpestønad i rådata
                                        )
                                    ).get()
                                    log.info("Prosessering fullført")
                                }
                            }
                        } catch (ex: Throwable) {
                            transactionTemplate.execute {
                                barnetrygdmottaker.retry(ex.stackTraceToString()).let {
                                    if (it.status is Barnetrygdmottaker.Status.Feilet) {
                                        log.error("Gir opp videre prosessering av melding")
                                    }
                                    barnetrygdmottakerRepository.updateStatus(it)
                                }
                            }
                            throw ex
                        }
                    }
                }
            }
        }
    }

    private fun createKafkaMessage(
        barnetrygdmottaker: Barnetrygdmottaker,
        persongrunnlag: List<PersongrunnlagMelding.Persongrunnlag>,
        rådata: Rådata
    ): ProducerRecord<String, String> {
        return ProducerRecord(
            Topics.Omsorgsopptjening.NAME,
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