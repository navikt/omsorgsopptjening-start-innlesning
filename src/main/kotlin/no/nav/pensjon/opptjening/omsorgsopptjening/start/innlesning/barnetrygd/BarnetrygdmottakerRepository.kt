package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.mapper
import no.nav.pensjon.opptjening.omsorgsopptjening.felles.serialize
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.time.Clock
import java.time.Instant
import java.util.UUID

inline fun <reified T> List<T>.serialize(): String {
    val listType = mapper.typeFactory.constructCollectionLikeType(List::class.java, T::class.java)
    return mapper.writerFor(listType).writeValueAsString(this)
}

inline fun <reified T> String.deserializeList(): List<T> {
    val listType = mapper.typeFactory.constructCollectionLikeType(List::class.java, T::class.java)
    return mapper.readerFor(listType).readValue(this)
}

@Component
class BarnetrygdmottakerRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val clock: Clock = Clock.systemUTC()
) {
    fun save(melding: Barnetrygdmottaker): Barnetrygdmottaker {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(
            """insert into barnetrygdmottaker (ident, correlation_id, request_id) values (:ident, :correlation_id, :request_id)""",
            MapSqlParameterSource(
                mapOf<String, Any>(
                    "ident" to melding.ident,
                    "correlation_id" to melding.correlationId,
                    "request_id" to melding.requestId,
                ),
            ),
            keyHolder
        )
        jdbcTemplate.update(
            """insert into barnetrygdmottaker_status (id, status, statushistorikk) values (:id, to_json(:status::json), to_json(:statushistorikk::json))""",
            MapSqlParameterSource(
                mapOf<String, Any>(
                    "id" to keyHolder.keys!!["id"] as UUID,
                    "status" to serialize(melding.status),
                    "statushistorikk" to melding.statushistorikk.serialize()
                ),
            ),
        )
        return find(keyHolder.keys!!["id"] as UUID)
    }

    fun updateStatus(melding: Barnetrygdmottaker) {
        jdbcTemplate.update(
            """update barnetrygdmottaker_status set status = to_json(:status::json), statushistorikk = to_json(:statushistorikk::json) where id = :id""",
            MapSqlParameterSource(
                mapOf<String, Any>(
                    "id" to melding.id!!,
                    "status" to serialize(melding.status),
                    "statushistorikk" to melding.statushistorikk.serialize()
                ),
            ),
        )
    }

    fun find(id: UUID): Barnetrygdmottaker {
        return jdbcTemplate.query(
            """select b.*, bs.statushistorikk, i.id as request_id, i.år from barnetrygdmottaker b join barnetrygdmottaker_status bs on b.id = bs.id join innlesing i on i.id = b.request_id where b.id = :id""",
            mapOf<String, Any>(
                "id" to id
            ),
            BarnetrygdmottakerRowMapper()
        ).single()
    }

    /**
     * Utformet for å være mekanismen som tilrettelegger for at flere podder kan prosessere data i paralell.
     * "select for update skip locked" sørger for at raden som leses av en connection (pod) ikke vil plukkes opp av en
     * annen connection (pod) så lenge transaksjonen lever.
     */
    fun finnNesteUprosesserte(): Barnetrygdmottaker? {
        return jdbcTemplate.query(
            """select b.*, bs.statushistorikk, i.id as request_id, i.år from barnetrygdmottaker b join barnetrygdmottaker_status bs on b.id = bs.id join innlesing i on i.id = b.request_id where i.ferdig_tidspunkt is not null and (bs.status->>'type' = 'Klar') or (bs.status->>'type' = 'Retry' and (bs.status->>'karanteneTil')::timestamptz < (:now)::timestamptz) fetch first row only for update of b skip locked""",
            mapOf(
                "now" to Instant.now(clock).toString()
            ),
            BarnetrygdmottakerRowMapper()
        ).singleOrNull()
    }

    internal class BarnetrygdmottakerRowMapper : RowMapper<Barnetrygdmottaker> {
        override fun mapRow(rs: ResultSet, rowNum: Int): Barnetrygdmottaker {
            return Barnetrygdmottaker(
                id = UUID.fromString(rs.getString("id")),
                opprettet = rs.getTimestamp("opprettet").toInstant(),
                ident = rs.getString("ident"),
                år = rs.getInt("år"),
                correlationId = UUID.fromString(rs.getString("correlation_id")),
                statushistorikk = rs.getString("statushistorikk").deserializeList(),
                requestId = rs.getString("request_id")
            )
        }
    }
}