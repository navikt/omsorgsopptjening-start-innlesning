package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.RådataFraKilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Topics
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.OmsorgsgrunnlagMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.serialize
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Mdc
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.BarnetrygdClient
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.BestillBarnetrygdmottakereResponse
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.HentBarnetrygdResponse
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdmottakerRepository
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.hjelpestønad.HjelpestønadRepo
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

@Service
class BarnetrygdmottakerService(
    private val client: BarnetrygdClient,
    private val repo: BarnetrygdmottakerRepository,
    private val kafkaProducer: KafkaTemplate<String, String>,
    private val transactionTemplate: TransactionTemplate,
    private val hjelpestønadRepo: HjelpestønadRepo,
) {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }

    fun bestillPersonerMedBarnetrygd(ar: Int): BestillBarnetrygdmottakereResponse {
        return client.bestillBarnetrygdmottakere(ar)
    }

    fun process(): Barnetrygdmottaker? {
        return transactionTemplate.execute {
            repo.finnNesteUprosesserte()?.let { barnetrygdmottaker ->
                Mdc.scopedMdc(barnetrygdmottaker.correlationId) {
                    Mdc.scopedMdc(barnetrygdmottaker.innlesingId) {
                        try {
                            transactionTemplate.execute {
                                log.info("Prosesserer barnetrygdmottaker med id:${barnetrygdmottaker.id}")

                                log.info("Henter detaljer")
                                client.hentBarnetrygd(
                                    ident = barnetrygdmottaker.ident,
                                    ar = barnetrygdmottaker.år!!,
                                ).let {
                                    barnetrygdmottaker.handle(it)
                                }
                            }
                        } catch (ex: Throwable) {
                            transactionTemplate.execute {
                                barnetrygdmottaker.retry(ex.toString()).let {
                                    if (it.status is Barnetrygdmottaker.Status.Feilet) {
                                        log.error("Gir opp videre prosessering av melding")
                                    }
                                    repo.updateStatus(it)
                                }
                            }
                            throw ex
                        }
                    }
                }
            }
        }
    }

    private fun Barnetrygdmottaker.handle(response: HentBarnetrygdResponse): Barnetrygdmottaker {
        return when (response) {
            is HentBarnetrygdResponse.Feil -> {
                """Feil ved henting av detaljer om barnetrygd, httpStatus: ${response.status}, body: ${response.body}""".let { melding ->
                    log.warn(melding)
                    retry(melding)
                        .also { repo.updateStatus(it) }
                }
            }

            is HentBarnetrygdResponse.Ok -> {
                log.info("Publiserer detaljer til topic:${Topics.Omsorgsopptjening.NAME}")
                ferdig()
                    .also {
                        repo.updateStatus(it)
                        kafkaProducer.send(
                            createKafkaMessage(
                                barnetrygdmottaker = it,
                                saker =  hjelpestønadRepo.leggTilEventuellHjelpestønad(response.barnetrygdsaker),
                                rådataFraKilde = response.rådataFraKilde, //TODO legg til hjelpestønad i rådata
                            )
                        ).get()
                        log.info("Prosessering fullført")
                    }

            }
        }
    }

    private fun createKafkaMessage(
        barnetrygdmottaker: Barnetrygdmottaker,
        saker: List<OmsorgsgrunnlagMelding.Sak>,
        rådataFraKilde: RådataFraKilde
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
                OmsorgsgrunnlagMelding(
                    omsorgsyter = barnetrygdmottaker.ident,
                    saker = saker,
                    rådata = rådataFraKilde,
                    innlesingId = barnetrygdmottaker.innlesingId,
                    correlationId = barnetrygdmottaker.correlationId,
                )
            )
        )
    }
}