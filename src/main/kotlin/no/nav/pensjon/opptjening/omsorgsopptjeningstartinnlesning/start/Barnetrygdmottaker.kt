package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.start

import jakarta.persistence.*
import org.hibernate.annotations.ColumnDefault
import org.hibernate.annotations.DynamicInsert
import org.hibernate.annotations.Generated
import org.hibernate.annotations.GenerationTime
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset


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

    constructor()
    constructor(
        ident: String,
        år: Int
    ) : this() {
        apply {
            this.ident = ident
            this.ar = år
        }
    }

    fun markerProsessert() {
        prosessert = true
    }
}