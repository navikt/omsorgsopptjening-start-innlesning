package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.start

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "START_HISTORIKK")
class StartHistorikk(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name ="HISTORIKK_ID", nullable = false)
    var id: Long? = null,
    @Column(name ="KJORINGS_TIMESTAMP", nullable = false)
    var kjoringTimesamp: LocalDateTime? = null,
    @Column(name ="KJORINGS_AR", nullable = false)
    var kjoringsAr: String? = null,
    @Column(name ="TIMESTAMP", nullable = false)
    var timestamp: LocalDateTime= LocalDateTime.now()
)