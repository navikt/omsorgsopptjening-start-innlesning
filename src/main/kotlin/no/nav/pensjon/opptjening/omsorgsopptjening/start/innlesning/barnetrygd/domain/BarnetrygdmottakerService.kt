package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.RådataFraKilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Topics
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Kilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.OmsorgsgrunnlagMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Omsorgstype
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.serialize
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Mdc
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.BarnetrygdClient
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.BestillBarnetrygdmottakereResponse
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.external.HentBarnetrygdResponse
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.BarnetrygdmottakerRepository
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class BarnetrygdmottakerService(
    private val client: BarnetrygdClient,
    private val repo: BarnetrygdmottakerRepository,
    private val kafkaProducer: KafkaTemplate<String, String>,
) {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }

    fun bestillPersonerMedBarnetrygd(ar: Int): BestillBarnetrygdmottakereResponse {
        return client.bestillBarnetrygdmottakere(ar)
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun process(): Barnetrygdmottaker? {
        return repo.finnNesteUprosesserte()?.let { barnetrygdmottaker ->
            Mdc.scopedMdc(barnetrygdmottaker.correlationId) {
                Mdc.scopedMdc(barnetrygdmottaker.innlesingId) {
                    try {
                        log.info("Prosesserer barnetrygdmottaker med id:${barnetrygdmottaker.id}")

                        log.info("Henter detaljer")
                        client.hentBarnetrygd(
                            ident = barnetrygdmottaker.ident,
                            ar = barnetrygdmottaker.år!!,
                        ).let {
                            barnetrygdmottaker.handle(it)
                        }
                    } catch (exception: Throwable) {
                        log.warn("Exception caught while processing id: ${barnetrygdmottaker.id}, exeption:$exception")
                        statusoppdatering.markerForRetry(barnetrygdmottaker, exception)
                        throw exception
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
                                saker = response.barnetrygdsaker,
                                rådataFraKilde = response.rådataFraKilde,
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
                    omsorgstype = Omsorgstype.BARNETRYGD,
                    kilde = Kilde.BARNETRYGD,
                    saker = saker,
                    rådata = rådataFraKilde,
                    innlesingId = barnetrygdmottaker.innlesingId,
                    correlationId = barnetrygdmottaker.correlationId,
                )
            )
        )
    }

    @Autowired
    private lateinit var statusoppdatering: Statusoppdatering

    /**
     * https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html
     *
     * "In proxy mode (which is the default), only external method calls coming in through the proxy are intercepted.
     * This means that self-invocation (in effect, a method within the target object calling another method of the target object)
     * does not lead to an actual transaction at runtime even if the invoked method is marked with @Transactional.
     * Also, the proxy must be fully initialized to provide the expected behavior, so you should not rely on this feature
     * in your initialization code - for example, in a @PostConstruct method."
     */
    @Component
    private class Statusoppdatering(
        private val repo: BarnetrygdmottakerRepository,
    ) {
        @Transactional(rollbackFor = [Throwable::class], propagation = Propagation.REQUIRES_NEW)
        fun markerForRetry(barnetrygdmottaker: Barnetrygdmottaker, exception: Throwable) {
            barnetrygdmottaker.retry(exception.toString()).let {
                if (it.status is Barnetrygdmottaker.Status.Feilet) {
                    log.error("Gir opp videre prosessering av melding")
                }
                repo.updateStatus(it)
            }
        }
    }
}