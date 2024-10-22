package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.*
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Rådata
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.PersongrunnlagMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Barnetrygdinformasjon
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Barnetrygdmottaker
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository.PersonSerialization.toPerson
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.time.Clock
import java.time.Instant
import java.util.*
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

@Component
class BarnetrygdinformasjonRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val clock: Clock = Clock.systemUTC()
) {
    fun insert(barnetrygdinformasjon: Barnetrygdinformasjon) {
        jdbcTemplate.update(
            """insert into barnetrygdinformasjon(
                |id,
                |barnetrygdmottaker_id,
                |created,
                |ident,
                |persongrunnlag,
                |rådata,
                |correlationId,
                |innlesingId,
                |status,
                |lockId,
                |lockTime
            |) values (
                |:id,
                |:barnetrygdmottaker_id,
                |:created::timestamptz,
                |:ident,
                |to_jsonb(:persongrunnlag::jsonb),
                |to_jsonb(:rådata::jsonb),
                |:correlationId,
                |:innlesingId,
                |:status,
                |:lockId,
                |:lockTime)""".trimMargin(),
            mapOf<String, Any?>(
                "id" to barnetrygdinformasjon.id,
                "barnetrygdmottaker_id" to barnetrygdinformasjon.barnetrygdmottakerId,
                "created" to Instant.now().toString(),
                "ident" to barnetrygdinformasjon.ident,
                "persongrunnlag" to serialize(barnetrygdinformasjon.persongrunnlag),
                "rådata" to serialize(barnetrygdinformasjon.rådata),
                "correlationId" to barnetrygdinformasjon.correlationId,
                "innlesingId" to barnetrygdinformasjon.innlesingId,
                "status" to when (barnetrygdinformasjon.status) {
                    Barnetrygdinformasjon.Status.KLAR -> "Klar"
                    Barnetrygdinformasjon.Status.SENDT -> "Sendt"
                },
                "lockId" to null,
                "lockTime" to null
            )
        )
    }

    fun hent(id: UUID): Barnetrygdinformasjon? {
        return jdbcTemplate.queryForObject(
            "select * from barnetrygdinformasjon where id = :id",
            mapOf<String, Any>(
                "id" to id
            ),
            BarnetrygdinformasjonRowMapper()
        )
    }

    fun finnNesteTilBehandling(innlesingId: InnlesingId, antall: Int): Locked {
        val lockId = UUID.randomUUID()
        val id: List<UUID> =
            finnNesteKlarTilBehandling(lockId, innlesingId, antall).ifEmpty {
                finnNesteForRetry(
                    lockId,
                    innlesingId,
                    antall
                )
            }
        return Locked(lockId, id.map { hent(it)!! })
    }

    fun frigi(locked: Locked) {
        jdbcTemplate.update(
            """update barnetrygdmottaker set lockId = null, lockTime = null where lockId = :lockId""",
            mapOf<String, Any>(
                "lockId" to locked.lockId,
            )
        )
    }

    fun finnAlle(id: InnlesingId): List<Barnetrygdinformasjon> {
        return jdbcTemplate.query(
            """select *
                | from barnetrygdinformasjon
                | where innlesingId = :id""".trimMargin(),
            mapOf<String, Any>(
                "id" to id
            ),
            BarnetrygdinformasjonRowMapper()
        )
    }

    /**
     * Utformet for å være mekanismen som tilrettelegger for at flere podder kan prosessere data i paralell.
     * "select for update skip locked" sørger for at raden som leses av en connection (pod) ikke vil plukkes opp av en
     * annen connection (pod) så lenge transaksjonen lever.
     */
    fun finnNesteKlarTilBehandling(lockId: UUID, innlesingId: InnlesingId, antall: Int): List<UUID> {
        val now = Instant.now(clock).toString()
        jdbcTemplate.update(
            """update barnetrygdmottaker set lockId = :lockId, lockTime = :now::timestamptz
                | where id in (
                | select id 
                | from barnetrygdmottaker
                | where status_type = 'Klar'
                | and innlesing_id = :innlesingId
                | and lockId is null
                | order by id asc
                | fetch first :antall rows only for update skip locked)
           """.trimMargin(),
            mapOf(
                "now" to now,
                "innlesingId" to innlesingId.toUUID().toString(),
                "antall" to antall,
                "lockId" to lockId,
            ),
        )

        return jdbcTemplate.queryForList(
            """select id from barnetrygdmottaker where lockId = :lockId""".trimMargin(),
            mapOf(
                "lockId" to lockId
            ),
            UUID::class.java
        )
    }

    fun finnNesteForRetry(lockId: UUID, innlesingId: InnlesingId, antall: Int): List<UUID> {
        val now = Instant.now(clock).toString()
        jdbcTemplate.update(
            """update barnetrygdmottaker
               |set lockId = :lockId, lockTime = :now::timestamptz
               |where id in (
               |select id
               | from barnetrygdmottaker
               | where status_type = 'Retry' 
               |and karantene_til < (:now)::timestamptz
               | and karantene_til is not null 
               | and innlesing_id = :innlesingId
               | and lockId is null
               | order by karantene_til asc 
               | fetch first :antall rows only for update skip locked)
           """.trimMargin(),
            mapOf(
                "now" to now,
                "innlesingId" to innlesingId.toUUID().toString(),
                "antall" to antall,
                "lockId" to lockId,
            ),
        )
        return jdbcTemplate.queryForList(
            """select id from barnetrygdmottaker where lockId = :lockId""",
            mapOf(
                "lockId" to lockId,
            ),
            UUID::class.java
        )
    }

    fun finnAntallMottakereMedStatusForInnlesing(
        kclass: KClass<*>,
        innlesingId: InnlesingId
    ): Long {
        val name = kclass.simpleName!!
        return jdbcTemplate.queryForObject(
            """select count(*) 
             | from barnetrygdmottaker b
             | join innlesing i on i.id = b.innlesing_id
             | where i.id = :innlesingId 
             | and b.status_type = :status""".trimMargin(),
            mapOf(
                "now" to Instant.now(clock).toString(),
                "innlesingId" to innlesingId.toString(),
                "status" to name,
            ),
            Long::class.java,
        )!!
    }

    fun finnAntallMottakereMedStatus(kclass: KClass<*>): Long {
        val name = kclass.simpleName!!
        return jdbcTemplate.queryForObject(
            """select count(*) 
                |from barnetrygdmottaker b, innlesing i
                |where b.innlesing_id = i.id 
                |and b.status_type = :status""".trimMargin(),
            mapOf(
                "now" to Instant.now(clock).toString(),
                "status" to name
            ),
            Long::class.java,
        )!!
    }

    fun oppdaterFeiledeRaderTilKlar(innlesingId: UUID): Int {
        val nyStatus = serialize(Barnetrygdmottaker.Status.Klar())
        return jdbcTemplate.update(
            //language=postgres-psql
            """
            update barnetrygdmottaker 
             set statushistorikk = statushistorikk || (:nyStatus::jsonb),
             status_type = 'Klar'
             where innlesing_id = :innlesingId and status_type = 'Feilet'
        """.trimIndent(),
            mapOf<String, Any>(
                "nyStatus" to nyStatus,
                "innlesingId" to innlesingId.toString(),
            )
        )
    }

    fun frigiGamleLåser(): Int {
        val oneHourAgo = Instant.now(clock).minus(1.hours.toJavaDuration()).toString()
        return jdbcTemplate.update(
            """update barnetrygdmottaker set lockId = null, lockTime = null 
            |where lockId is not null and lockTime < :oneHourAgo::timestamptz""".trimMargin(),
            mapOf<String, Any>(
                "oneHourAgo" to oneHourAgo
            )
        )
    }

    internal class BarnetrygdinformasjonRowMapper : RowMapper<Barnetrygdinformasjon> {
        override fun mapRow(rs: ResultSet, rowNum: Int): Barnetrygdinformasjon {
            return Barnetrygdinformasjon(
                id = UUID.fromString(rs.getString("id")),
                barnetrygdmottakerId = UUID.fromString(rs.getString("barnetrygdmottaker_id")),
                created = rs.getTimestamp("created").toInstant(),
                ident = rs.getString("ident"),
                persongrunnlag = deserialize<List<PersongrunnlagMelding.Persongrunnlag>>(rs.getString("persongrunnlag")),
                rådata = deserialize<Rådata>(rs.getString("rådata")),
                correlationId = UUID.fromString(rs.getString("correlationId")),
                innlesingId = UUID.fromString(rs.getString("innlesingId")),
                status = when (val value = rs.getString("status")) {
                    "Klar" -> Barnetrygdinformasjon.Status.KLAR
                    "Sendt" -> Barnetrygdinformasjon.Status.SENDT
                    else -> throw UgyldigBarnetrygdinformasjonException("Ukjent status: $value")
                }
            )
        }
    }

    data class Locked(val lockId: UUID, val data: List<Barnetrygdinformasjon>)

    class UgyldigBarnetrygdinformasjonException(msg: String) : RuntimeException(msg)
}