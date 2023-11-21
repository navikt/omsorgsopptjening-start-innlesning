package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.deserializeList
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.serialize
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.serializeList
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Barnetrygdmottaker
import org.jetbrains.annotations.TestOnly
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.reflect.KClass

@Component
class BarnetrygdmottakerRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val clock: Clock = Clock.systemUTC()
) {
    @TestOnly
    //TODO ikke bruk denne
    fun insert(barnetrygdmottaker: Barnetrygdmottaker.Transient): Barnetrygdmottaker.Mottatt {
        return insertBatch(listOf(barnetrygdmottaker))
            .let { finnAlle(barnetrygdmottaker.innlesingId).single { it.ident == barnetrygdmottaker.ident } }
    }

    fun insertBatch(barnetrygdmottaker: List<Barnetrygdmottaker.Transient>) {
        jdbcTemplate.batchUpdate(
            """with btm as (insert into barnetrygdmottaker (ident, correlation_id, innlesing_id) 
                |values (:ident, :correlation_id, :innlesing_id) returning id as btm_id) 
                |insert into barnetrygdmottaker_status (id, status, statushistorikk) 
                |values ((select btm_id from btm), to_jsonb(:status::jsonb), to_jsonb(:statushistorikk::jsonb))""".trimMargin(),
            barnetrygdmottaker
                .map {
                    mapOf(
                        "ident" to it.ident,
                        "correlation_id" to it.correlationId.toString(),
                        "innlesing_id" to it.innlesingId.toString(),
                        "status" to serialize(it.status),
                        "statushistorikk" to it.statushistorikk.serializeList(),
                    )
                }.toTypedArray()
        )
    }

    fun updateStatus(barnetrygdmottaker: Barnetrygdmottaker.Mottatt) {
        jdbcTemplate.update(
            """update barnetrygdmottaker_status set status = to_jsonb(:status::jsonb), statushistorikk = to_jsonb(:statushistorikk::jsonb) where id = :id""",
            MapSqlParameterSource(
                mapOf<String, Any>(
                    "id" to barnetrygdmottaker.id,
                    "status" to serialize(barnetrygdmottaker.status),
                    "statushistorikk" to barnetrygdmottaker.statushistorikk.serializeList(),
                ),
            ),
        )
    }

    fun find(id: UUID): Barnetrygdmottaker.Mottatt? {
        return jdbcTemplate.query(
            """select b.*, bs.statushistorikk, i.id as innlesing_id, i.år from barnetrygdmottaker b join barnetrygdmottaker_status bs on b.id = bs.id join innlesing i on i.id = b.innlesing_id where b.id = :id""",
            mapOf<String, Any>(
                "id" to id
            ),
            BarnetrygdmottakerRowMapper()
        ).singleOrNull()
    }

    fun finnAlle(id: InnlesingId): List<Barnetrygdmottaker.Mottatt> {
        return jdbcTemplate.query(
            """select b.*, bs.statushistorikk, i.id as innlesing_id, i.år from barnetrygdmottaker b join barnetrygdmottaker_status bs on b.id = bs.id join innlesing i on i.id = b.innlesing_id where i.id = :id""",
            mapOf<String, Any>(
                "id" to id.toString()
            ),
            BarnetrygdmottakerRowMapper()
        )
    }

    /**
     * Utformet for å være mekanismen som tilrettelegger for at flere podder kan prosessere data i paralell.
     * "select for update skip locked" sørger for at raden som leses av en connection (pod) ikke vil plukkes opp av en
     * annen connection (pod) så lenge transaksjonen lever.
     */
    fun finnNesteUprosesserte(): Barnetrygdmottaker.Mottatt? {
        return jdbcTemplate.query(
           """select b.*, bs.statushistorikk, i.id as innlesing_id, i.år 
                | from barnetrygdmottaker b 
                | join (SELECT * from barnetrygdmottaker_status 
                |        WHERE (status->>'type' = 'Klar') OR (status->>'type' = 'Retry')) bs
                | on b.id = bs.id 
                | join innlesing i on i.id = b.innlesing_id 
                | where i.ferdig_tidspunkt is not null 
                |   and (bs.status->>'type' = 'Klar') 
                |   or (bs.status->>'type' = 'Retry' and (bs.status->>'karanteneTil')::timestamptz < (:now)::timestamptz) 
                | fetch first row only for update of b skip locked""".trimMargin(),
//
            /*
            """SELECT
                   | b.*,
                   | bs.statushistorikk,
                   | i.id AS innlesing_id,
                   | i.år
                   | FROM
                   | barnetrygdmottaker b,
                   | innlesing i,
                   | (SELECT * from barnetrygdmottaker_status WHERE
                   |      (status->>'type' = 'Klar') OR (status->>'type' = 'Retry')
                   |      LIMIT 5000) bs
                   | WHERE
                   | b.id = bs.id
                   | AND i.id = b.innlesing_id
                   | AND i.ferdig_tidspunkt IS NOT NULL
                   | AND ((bs.status->>'type' = 'Klar') OR (bs.status->>'karanteneTil')::timestamptz < (current_timestamp)::timestamptz)
                   | FOR UPDATE OF b SKIP LOCKED""".trimMargin(),
*/
            mapOf(
                "now" to Instant.now(clock).toString()
            ),
            BarnetrygdmottakerRowMapper()
        ).singleOrNull()
    }

    fun finnAntallMottakereMedStatusForInnlesing(
        kclass: KClass<*>,
        innlesingId: InnlesingId
    ): Long {
        val name = kclass.simpleName!!
        return jdbcTemplate.queryForObject(
            """select count(*) 
                |from barnetrygdmottaker b, barnetrygdmottaker_status bs, innlesing i
                |where b.id = bs.id 
                |and b.innlesing_id = i.id 
                |and i.id = :innlesingId 
                |and (bs.status->>'type' = :status)""".trimMargin(),
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
                |from barnetrygdmottaker b, barnetrygdmottaker_status bs, innlesing i
                |where b.id = bs.id 
                |and b.innlesing_id = i.id 
                |and (bs.status->>'type' = :status """.trimMargin(),
            mapOf(
                "now" to Instant.now(clock).toString(),
                "status" to name
            ),
            Long::class.java,
        )!!
    }


    internal class BarnetrygdmottakerRowMapper : RowMapper<Barnetrygdmottaker.Mottatt> {
        override fun mapRow(rs: ResultSet, rowNum: Int): Barnetrygdmottaker.Mottatt {
            return Barnetrygdmottaker.Mottatt(
                id = UUID.fromString(rs.getString("id")),
                opprettet = rs.getTimestamp("opprettet").toInstant(),
                ident = rs.getString("ident"),
                år = rs.getInt("år"),
                correlationId = CorrelationId.fromString(rs.getString("correlation_id")),
                statushistorikk = rs.getString("statushistorikk").deserializeList(),
                innlesingId = InnlesingId.fromString(rs.getString("innlesing_id"))
            )
        }
    }
}