package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Component
import java.sql.ResultSet

@Component
class InnlesingRepo(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) {
    fun forespurt(innlesing: Innlesing): Innlesing {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(
            """insert into innlesing (id, år, forespurt_tidspunkt) values (:id, :ar, now())""",
            MapSqlParameterSource(
                mapOf<String, Any>(
                    "id" to innlesing.id.toString(),
                    "ar" to innlesing.år,
                ),
            ),
            keyHolder
        )
        return finn(keyHolder.keys!!["id"] as String)!!
    }

    fun start(id: String): Innlesing {
        jdbcTemplate.update(
            """update innlesing set start_tidspunkt = now() where id = :id""",
            MapSqlParameterSource(
                mapOf<String, Any>(
                    "id" to id,
                ),
            ),
        )
        return finn(id)!!
    }

    fun fullført(id: String): Innlesing {
        jdbcTemplate.update(
            """update innlesing set ferdig_tidspunkt = now() where id = :id""",
            MapSqlParameterSource(
                mapOf<String, Any>(
                    "id" to id,
                ),
            ),
        )
        return finn(id)!!
    }

    fun finn(id: String): Innlesing? {
        return jdbcTemplate.query(
            """select * from innlesing where id = :id""",
            MapSqlParameterSource(
                mapOf<String, Any>(
                    "id" to id
                )
            ),
            InnlesingRowMapper()
        ).singleOrNull()
    }

    private class InnlesingRowMapper : RowMapper<Innlesing> {
        override fun mapRow(rs: ResultSet, rowNum: Int): Innlesing? {
            return Innlesing(
                id = InnlesingId.fromString(rs.getString("id")),
                år = rs.getInt("år"),
                forespurtTidspunkt = rs.getTimestamp("forespurt_tidspunkt").toInstant(),
                startTidspunkt = rs.getTimestamp("start_tidspunkt")?.toInstant(),
                ferdigTidspunkt = rs.getTimestamp("ferdig_tidspunkt")?.toInstant()
            )
        }
    }
}