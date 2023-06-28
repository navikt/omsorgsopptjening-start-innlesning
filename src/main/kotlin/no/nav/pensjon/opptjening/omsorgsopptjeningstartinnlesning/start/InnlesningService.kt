package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.start

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.deserialize
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.BarnetrygdMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.BarnetrygdSak
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.serialize
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class InnlesningService(
    private val client: BarnetrygdClient,
    private val repository: BarnetrygdmottakerRepository,
    private val kafkaProducer: KafkaTemplate<String, String>,
) {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }

    fun initierSendingAvIdenter(ar: Int): BarnetrygdClientResponse {
        return client.initierSendingAvIdenter(ar)
    }

    fun prosesserBarnetrygdmottakere() {
        repository.finnNesteUprosesserte()?.let { barnetrygdmottaker ->
            val detaljer = client.hentBarnetrygdDetaljer(
                ident = barnetrygdmottaker.ident!!,
                ar = barnetrygdmottaker.ar!!
            )
            when (detaljer) {
                is BarnetrygdClientResponse.Feil -> {
                    throw RuntimeException("Feil")
                }

                is BarnetrygdClientResponse.Ok -> {
                    log.info("Publishing details for id:${barnetrygdmottaker.id} to topic:todo-topic")
                    kafkaProducer.send(
                        "todo-topic",
                        serialize(
                            BarnetrygdMelding(
                                ident = barnetrygdmottaker.ident!!,
                                list = deserialize<List<BarnetrygdSak>>(detaljer.body!!)
                            )
                        )
                    )
                    log.info("Processing completed for id:${barnetrygdmottaker.id}")
                    log.info("Saving state for id:${barnetrygdmottaker.id}")
                    barnetrygdmottaker.markerProsessert()
                    repository.save(barnetrygdmottaker)
                }
            }
        }
    }
}