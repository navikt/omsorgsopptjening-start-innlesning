package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.start

import jakarta.persistence.*


@Entity(name = "barnetrygdmottaker")
class Barnetrygdmottaker(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,
    @Column(name = "ident")
    val ident: String? = null,
    @Column(name = "ar")
    val ar: Int? = null
)