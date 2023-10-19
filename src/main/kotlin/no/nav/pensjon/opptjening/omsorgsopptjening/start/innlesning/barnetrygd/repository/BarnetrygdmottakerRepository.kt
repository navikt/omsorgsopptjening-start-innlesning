package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.CorrelationId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.deserializeList
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.serialize
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.serializeList
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.Barnetrygdmottaker
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Component
class BarnetrygdmottakerRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val clock: Clock = Clock.systemUTC()
) {
    fun insert(barnetrygdmottaker: Barnetrygdmottaker.Transient): Barnetrygdmottaker.Mottatt {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(
            """insert into barnetrygdmottaker (ident, correlation_id, innlesing_id) values (:ident, :correlation_id, :innlesing_id)""",
            MapSqlParameterSource(
                mapOf<String, Any>(
                    "ident" to barnetrygdmottaker.ident,
                    "correlation_id" to barnetrygdmottaker.correlationId.toString(),
                    "innlesing_id" to barnetrygdmottaker.innlesingId.toString(),
                ),
            ),
            keyHolder
        )
        jdbcTemplate.update(
            """insert into barnetrygdmottaker_status (id, status, statushistorikk, kort_status) values (:id, to_jsonb(:status::jsonb), to_jsonb(:statushistorikk::jsonb),:kort_status)""",
            MapSqlParameterSource(
                mapOf<String, Any>(
                    "id" to keyHolder.keys!!["id"] as UUID,
                    "status" to serialize(barnetrygdmottaker.status),
                    "statushistorikk" to barnetrygdmottaker.statushistorikk.serializeList(),
                    "kort_status" to barnetrygdmottaker.status.kortStatus.toString(),
                ),
            ),
        )
        return find(keyHolder.keys!!["id"] as UUID)!!
    }

    fun updateStatus(barnetrygdmottaker: Barnetrygdmottaker.Mottatt) {
        println("XXXX ${serialize(barnetrygdmottaker.status)}")
        jdbcTemplate.update(
            """update barnetrygdmottaker_status set status = to_jsonb(:status::jsonb), statushistorikk = to_jsonb(:statushistorikk::jsonb),kort_status = :kort_status where id = :id""",
            MapSqlParameterSource(
                mapOf<String, Any>(
                    "id" to barnetrygdmottaker.id,
                    "status" to serialize(barnetrygdmottaker.status),
                    "statushistorikk" to barnetrygdmottaker.statushistorikk.serializeList(),
                    "kort_status" to barnetrygdmottaker.status.kortStatus.toString(),
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

    /**
     * Utformet for å være mekanismen som tilrettelegger for at flere podder kan prosessere data i paralell.
     * "select for update skip locked" sørger for at raden som leses av en connection (pod) ikke vil plukkes opp av en
     * annen connection (pod) så lenge transaksjonen lever.
     */
    fun finnNesteUprosesserte(): Barnetrygdmottaker.Mottatt? {
        return jdbcTemplate.query(
            """select b.*, bs.statushistorikk, i.id as innlesing_id, i.år from barnetrygdmottaker b join barnetrygdmottaker_status bs on b.id = bs.id join innlesing i on i.id = b.innlesing_id where i.ferdig_tidspunkt is not null and (bs.status->>'type' = 'Klar') or (bs.status->>'type' = 'Retry' and (bs.status->>'karanteneTil')::timestamptz < (:now)::timestamptz) fetch first row only for update of b skip locked""",
            mapOf(
                "now" to Instant.now(clock).toString()
            ),
            BarnetrygdmottakerRowMapper()
        ).singleOrNull()
    }

    fun finnAntallMottakereMedStatusForInnlesing(kortStatus: Barnetrygdmottaker.KortStatus, innlesingId: InnlesingId): Long {
        return jdbcTemplate.queryForObject(
            """select count(*) 
                |from barnetrygdmottaker b, barnetrygdmottaker_status bs, innlesing i
                |where b.id = bs.id 
                |and b.innlesing_id = i.id 
                |and i.id = :innlesingId 
                |and bs.kort_status = :kort_status """.trimMargin(),
            mapOf(
                "now" to Instant.now(clock).toString(),
                "innlesingId" to innlesingId.toString(),
                "kort_status" to kortStatus.toString(),
            ),
            Long::class.java,
        )!!
    }

    fun finnAntallMottakereMedStatus(kortStatus: Barnetrygdmottaker.KortStatus): Long {
        return jdbcTemplate.queryForObject(
            """select count(*) 
                |from barnetrygdmottaker b, barnetrygdmottaker_status bs, innlesing i
                |where b.id = bs.id 
                |and b.innlesing_id = i.id 
                |and bs.kort_status = :kort_status """.trimMargin(),
            mapOf(
                "now" to Instant.now(clock).toString(),
                "kort_status" to kortStatus.toString(),
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