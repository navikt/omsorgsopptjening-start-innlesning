package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.*
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.deserialize
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.serialize
import org.hibernate.annotations.DynamicInsert
import org.hibernate.annotations.Type
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

    @Column(name = "opprettet", nullable = false)
    var opprettet: Instant = Instant.now(Clock.systemUTC())

    @Column(name = "correlationid", nullable = false)
    var correlationId: String = CorrelationId.generate()

    @Column(name = "status", nullable = false)
    @Type(JsonType::class)
    @Convert(converter = StatusConverter::class)
    var status: Status = Status.Klar(opprettet)

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
    fun ferdig() {
        status = status.ferdig()
    }
    fun retry() {
        status = status.retry()
    }
}

@Converter
class StatusConverter(): AttributeConverter<Status, String> {
    override fun convertToDatabaseColumn(attribute: Status?): String {
        return serialize(attribute!!)
    }

    override fun convertToEntityAttribute(dbData: String?): Status {
        return deserialize(dbData!!)
    }

}