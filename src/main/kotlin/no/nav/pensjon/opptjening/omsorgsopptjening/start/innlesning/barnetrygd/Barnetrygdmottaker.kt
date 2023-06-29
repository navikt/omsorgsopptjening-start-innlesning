package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import jakarta.persistence.*
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import org.hibernate.annotations.DynamicInsert
import java.time.Clock
import java.time.Instant


@Entity(name = "barnetrygdmottaker")
@DynamicInsert
class Barnetrygdmottaker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    val id: Long? = null

    @Column(name = "ident", nullable = false)
    var ident: String? = null

    @Column(name = "ar", nullable = false)
    var ar: Int? = null

    @Column(name = "prosessert", nullable = false)
    var prosessert: Boolean = false

    @Column(name = "opprettet", nullable = false)
    var opprettet: Instant = Instant.now(Clock.systemUTC())

    @Column(name = "correlationid", nullable = false)
    var correlationId: String = CorrelationId.generate()

    constructor()
    constructor(
        ident: String,
        år: Int,
        correlationId: String,
    ) : this() {
        apply {
            this.ident = ident
            this.ar = år
            this.correlationId = correlationId
        }
    }

    fun markerProsessert() {
        prosessert = true
    }
}