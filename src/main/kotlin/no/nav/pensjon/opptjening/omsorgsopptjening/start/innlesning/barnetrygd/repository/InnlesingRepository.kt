package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.repository

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.InnlesingId
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.BarnetrygdInnlesing
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.domain.År
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.util.*

@Component
class InnlesingRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) {
    fun bestilt(innlesing: BarnetrygdInnlesing.Bestilt): BarnetrygdInnlesing {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(
            """insert into innlesing (id, år, forespurt_tidspunkt) values (:id, :ar, :forespurt_tidspunkt::timestamptz)""",
            MapSqlParameterSource(
                mapOf<String, Any>(
                    "id" to innlesing.id.toString(),
                    "ar" to innlesing.år.value,
                    "forespurt_tidspunkt" to innlesing.forespurtTidspunkt.toString()
                ),
            ),
            keyHolder
        )
        return finn(keyHolder.keys!!["id"] as String)!!
    }

    fun start(startet: BarnetrygdInnlesing.Startet): BarnetrygdInnlesing {
        jdbcTemplate.update(
            """update innlesing set start_tidspunkt = :start_tidspunkt::timestamptz, forventet_antall_identiteter = :forventet_antall_identiteter where id = :id""",
            MapSqlParameterSource(
                mapOf<String, Any>(
                    "id" to startet.id.toString(),
                    "start_tidspunkt" to startet.startTidspunkt.toString(),
                ).let {
                    if (startet.forventetAntallIdentiteter == null) it
                    else it.plus("forventet_antall_identiteter" to (startet.forventetAntallIdentiteter))
                }
                //"antall_identiteter" to startet.antallIdenterLest,
            ),
        )
        return finn(startet.id.toString())!! //TODO
    }

    fun fullført(ferdig: BarnetrygdInnlesing.Ferdig): BarnetrygdInnlesing {
        jdbcTemplate.update(
            """update innlesing set ferdig_tidspunkt = :ferdig_tidspunkt::timestamptz where id = :id""",
            MapSqlParameterSource(
                mapOf<String, Any>(
                    "id" to ferdig.id.toString(),
                    "ferdig_tidspunkt" to ferdig.ferdigTidspunkt.toString()
                ),
            ),
        )
        return finn(ferdig.id.toString())!!
    }

    fun finn(id: String): BarnetrygdInnlesing? {
        return jdbcTemplate.query(
            """select i.*, forventet_antall_identiteter, count(b) as antallLesteIdenter from innlesing i left join barnetrygdmottaker b on i.id = b.innlesing_id group by i.id having i.id = :id""",
            MapSqlParameterSource(
                mapOf<String, Any>(
                    "id" to id
                )
            ),
            InnlesingRowMapper()
        ).singleOrNull()
    }

    fun finnSisteInnlesing(): BarnetrygdInnlesing? {
        return jdbcTemplate.query(
            """select i.*, count(b) as antallLesteIdenter from innlesing i left join barnetrygdmottaker b on i.id = b.innlesing_id group by i.id order by i.forespurt_tidspunkt desc limit 1""",
            InnlesingRowMapper()
        ).singleOrNull()
    }

    fun finnAlleFullførte(): List<InnlesingId> {
        return jdbcTemplate.queryForList(
            """select id from innlesing where ferdig_tidspunkt is not null""",
            emptyMap<String, Any?>(),
            String::class.java
        ).map(InnlesingId::fromString)
    }

    fun invalider(id: UUID) {
        jdbcTemplate.update(
            """delete from innlesing where id = :id""",
            MapSqlParameterSource(
                mapOf<String, Any>(
                    "id" to id.toString(),
                ),
            ),
        )
    }

    private class InnlesingRowMapper : RowMapper<BarnetrygdInnlesing> {
        override fun mapRow(rs: ResultSet, rowNum: Int): BarnetrygdInnlesing {
            return BarnetrygdInnlesing.of(
                id = InnlesingId.fromString(rs.getString("id")),
                år = År(rs.getInt("år")),
                forespurtTidspunkt = rs.getTimestamp("forespurt_tidspunkt").toInstant(),
                startTidspunkt = rs.getTimestamp("start_tidspunkt")?.toInstant(),
                ferdigTidspunkt = rs.getTimestamp("ferdig_tidspunkt")?.toInstant(),
                antallIdenterLest = rs.getInt("antallLesteIdenter"),
                forventetAntallIdentiteter = rs.getLong("forventet_antall_identiteter")
            )
        }
    }
}