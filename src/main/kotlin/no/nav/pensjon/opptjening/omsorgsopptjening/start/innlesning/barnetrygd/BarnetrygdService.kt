package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.KafkaMessageType
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.RådataFraKilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Topics
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.barnetrygd.Barnetrygdmelding
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Kilde
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.OmsorgsgrunnlagMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.Omsorgstype
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.serialize
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.Mdc
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class BarnetrygdService(
    private val client: BarnetrygdClient,
    private val repo: BarnetrygdmottakerRepository,
    private val kafkaProducer: KafkaTemplate<String, String>,
) {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }

    fun initierSendingAvIdenter(ar: Int): HentBarnetygdmottakereResponse {
        return client.hentBarnetrygdmottakere(ar)
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun process(): Barnetrygdmottaker? {
        return repo.finnNesteUprosesserte()?.let { barnetrygdmottaker ->
            Mdc.scopedMdc(CorrelationId.name, barnetrygdmottaker.correlationId.toString()) {
                try {
                    log.info("Prosesserer barnetrygdmottaker med id:${barnetrygdmottaker.id}")

                    log.info("Henter detaljer")
                    client.hentBarnetrygd(
                        ident = barnetrygdmottaker.ident,
                        ar = barnetrygdmottaker.år!!,
                    ).let { response ->
                        when (response) {
                            is HentBarnetrygdResponse.Feil -> {
                                """Feil ved henting av detaljer om barnetrygd, httpStatus: ${response.status}, body: ${response.body}""".let { melding ->
                                    log.error(melding)
                                    barnetrygdmottaker.retry(melding)
                                        .also { repo.updateStatus(it) }
                                }
                            }

                            is HentBarnetrygdResponse.Ok -> {
                                log.info("Publiserer detaljer til topic:${Topics.Omsorgsopptjening.NAME}")
                                barnetrygdmottaker.ferdig()
                                    .also {
                                        kafkaProducer.send(
                                            createKafkaMessage(
                                                barnetrygdmottaker = it,
                                                saker = response.barnetrygdsaker
                                            )
                                        ).get()
                                        repo.updateStatus(it)
                                        log.info("Prosessering fullført")
                                    }

                            }
                        }
                    }
                } catch (exception: Throwable) {
                    log.error("Exception caught while processing id: ${barnetrygdmottaker.id}, exeption:$exception")
                    statusoppdatering.markerForRetry(barnetrygdmottaker, exception)
                    throw exception
                }
            }
        }
    }

    private fun createKafkaMessage(
        barnetrygdmottaker: Barnetrygdmottaker,
        saker: List<Barnetrygdmelding.Sak>
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
                    kjoreHash = barnetrygdmottaker.requestId,
                    kilde = Kilde.BARNETRYGD,
                    saker = saker.map { barnetrygdSak ->
                        OmsorgsgrunnlagMelding.Sak(
                            omsorgsyter = barnetrygdSak.fagsakEiersIdent,
                            vedtaksperioder = barnetrygdSak.barnetrygdPerioder.map {
                                OmsorgsgrunnlagMelding.VedtakPeriode(
                                    fom = it.stønadFom,
                                    tom = it.stønadTom,
                                    prosent = it.delingsprosentYtelse,
                                    omsorgsmottaker = it.personIdent
                                )
                            }
                        )
                    },
                    rådata = RådataFraKilde(serialize(saker))
                )
            ),
            setOf(
                RecordHeader(
                    KafkaMessageType.name,
                    KafkaMessageType.OMSORGSGRUNNLAG.toString().toByteArray()
                ),
                RecordHeader(
                    CorrelationId.name,
                    barnetrygdmottaker.correlationId.toString().toByteArray()
                )
            ),
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