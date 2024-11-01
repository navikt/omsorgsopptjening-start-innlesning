package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.deserialize
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Rådata
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.messages.domene.PersongrunnlagMelding
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.serialize
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Barnetrygdinformasjon
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Ident
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.time.Clock
import java.time.Instant
import java.util.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

@Component
class BarnetrygdinformasjonRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val clock: Clock = Clock.systemUTC()
) {
    // TODO: vurdere å bruke "on conflict update" eller en annen mekanisme for å kunne reprossesere uten manuell sletting
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
                "ident" to barnetrygdinformasjon.ident.value,
                "persongrunnlag" to serialize(barnetrygdinformasjon.persongrunnlag),
                "rådata" to serialize(barnetrygdinformasjon.rådata),
                "correlationId" to barnetrygdinformasjon.correlationId.toUUID(),
                "innlesingId" to barnetrygdinformasjon.innlesingId.toUUID(),
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

    fun finnNesteTilBehandling(antall: Int): Locked {
        val lockId = UUID.randomUUID()
        val id: List<UUID> = finnNesteKlarTilBehandling(lockId, antall)
        println("finnNesteTilBehandling: id.size=${id.size}")
        return Locked(lockId, id.map { hent(it)!! })
    }

    fun frigi(locked: Locked) {
        jdbcTemplate.update(
            """update barnetrygdinformasjon set lockId = null, lockTime = null where lockId = :lockId""",
            mapOf<String, Any>(
                "lockId" to locked.lockId,
            )
        )
    }

    fun oppdaterStatus(barnetrygdinformasjon: Barnetrygdinformasjon) {
        jdbcTemplate.update(
            """update barnetrygdinformasjon set status = :status where id = :id""",
            mapOf<String, Any>(
                "status" to when (barnetrygdinformasjon.status) {
                    Barnetrygdinformasjon.Status.KLAR -> "Klar"
                    Barnetrygdinformasjon.Status.SENDT -> "Sendt"
                },
                "id" to barnetrygdinformasjon.id
            )
        )
    }


    fun finnAlle(id: InnlesingId): List<Barnetrygdinformasjon> {
        return jdbcTemplate.query(
            """select *
                | from barnetrygdinformasjon
                | where innlesingId = :id""".trimMargin(),
            mapOf<String, Any>(
                "id" to id.toUUID()
            ),
            BarnetrygdinformasjonRowMapper()
        )
    }

    /**
     * Utformet for å være mekanismen som tilrettelegger for at flere podder kan prosessere data i paralell.
     * "select for update skip locked" sørger for at raden som leses av en connection (pod) ikke vil plukkes opp av en
     * annen connection (pod) så lenge transaksjonen lever.
     */
    fun finnNesteKlarTilBehandling(lockId: UUID, antall: Int): List<UUID> {
        val now = Instant.now(clock).toString()
        val updateCount = jdbcTemplate.update(
            """update barnetrygdinformasjon set lockId = :lockId, lockTime = :now::timestamptz
                | where id in (
                | select id 
                | from barnetrygdinformasjon
                | where status = 'Klar'
                | and lockId is null
                | order by id asc
                | fetch first :antall rows only for update skip locked)
           """.trimMargin(),
            mapOf(
                "now" to now,
                "antall" to antall,
                "lockId" to lockId,
            ),
        )
        println("finnNesteKlarTilBehandling.update count: $updateCount")

        return jdbcTemplate.queryForList(
            """select id from barnetrygdinformasjon where lockId = :lockId""".trimMargin(),
            mapOf(
                "lockId" to lockId
            ),
            UUID::class.java
        )
    }

    fun finnAntallMedStatus(status: Barnetrygdinformasjon.Status): Long {
        return jdbcTemplate.queryForObject(
            """select count(*) 
                |from barnetrygdinnlesing
                |and status = :status""".trimMargin(),
            mapOf(
                "status" to when (status) {
                    Barnetrygdinformasjon.Status.KLAR -> "Klar"
                    Barnetrygdinformasjon.Status.SENDT -> "Sendt"
                }
            ),
            Long::class.java,
        )!!
    }

    fun frigiGamleLåser(): Int {
        val oneHourAgo = Instant.now(clock).minus(1.hours.toJavaDuration()).toString()
        return jdbcTemplate.update(
            """update barnetrygdinformasjon set lockId = null, lockTime = null 
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
                ident = Ident(rs.getString("ident")),
                persongrunnlag = deserialize<List<PersongrunnlagMelding.Persongrunnlag>>(rs.getString("persongrunnlag")),
                rådata = deserialize<Rådata>(rs.getString("rådata")),
                correlationId = CorrelationId.fromString(rs.getString("correlationId")),
                innlesingId = InnlesingId.fromString(rs.getString("innlesingId")),
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