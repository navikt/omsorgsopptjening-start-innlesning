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
            """insert into barnetrygdmottaker (ident, correlation_id, innlesing_id, status, status_type, karantene_til, statushistorikk)
             | values (:ident, :correlation_id, :innlesing_id, to_jsonb(:status::jsonb), :status_type, :karantene_til::timestamptz, to_jsonb(:statushistorikk::jsonb))""".trimMargin(),
            barnetrygdmottaker
                .map {
                    mapOf(
                        "ident" to it.ident,
                        "correlation_id" to it.correlationId.toString(),
                        "innlesing_id" to it.innlesingId.toString(),
                        "status" to serialize(it.status),
                        "status_type" to when (it.status) {
                            is Barnetrygdmottaker.Status.Feilet -> "Feilet"
                            is Barnetrygdmottaker.Status.Ferdig -> "Ferdig"
                            is Barnetrygdmottaker.Status.Klar -> "Klar"
                            is Barnetrygdmottaker.Status.Retry -> "Retry"
                        },
                        "karantene_til" to when (val s = it.status) {
                            is Barnetrygdmottaker.Status.Retry -> s.karanteneTil.toString()
                            else -> null
                        },
                        "statushistorikk" to it.statushistorikk.serializeList(),
                    )
                }.toTypedArray()
        )
    }

    fun updateStatus(barnetrygdmottaker: Barnetrygdmottaker.Mottatt) {
        jdbcTemplate.update(
            """update barnetrygdmottaker 
                |set status = to_jsonb(:status::jsonb), 
                | statushistorikk = to_jsonb(:statushistorikk::jsonb) ,
                | status_type = :status_type,
                | karantene_til = :karantene_til::timestamptz
                | where id = :id""".trimMargin(),
            MapSqlParameterSource(
                mapOf<String, Any?>(
                    "id" to barnetrygdmottaker.id,
                    "status" to serialize(barnetrygdmottaker.status),
                    "status_type" to when (barnetrygdmottaker.status) {
                        is Barnetrygdmottaker.Status.Feilet -> "Feilet"
                        is Barnetrygdmottaker.Status.Ferdig -> "Ferdig"
                        is Barnetrygdmottaker.Status.Klar -> "Klar"
                        is Barnetrygdmottaker.Status.Retry -> "Retry"
                    },
                    "karantene_til" to when (val s = barnetrygdmottaker.status) {
                        is Barnetrygdmottaker.Status.Retry -> s.karanteneTil.toString()
                        else -> null
                    },
                    "statushistorikk" to barnetrygdmottaker.statushistorikk.serializeList(),
                ),
            ),
        )
    }

    fun finnNesteTilBehandling(innlesingId: InnlesingId, antall: Int): List<Barnetrygdmottaker.Mottatt> {
        val id: List<UUID> = finnNesteKlarTilBehandling(innlesingId, antall).ifEmpty { finnNesteForRetry(innlesingId, antall) }
        return id.map { find(it)!! }
    }


    fun find(id: UUID): Barnetrygdmottaker.Mottatt? {
        return jdbcTemplate.query(
            """select b.*, i.id as innlesing_id, i.år
                | from barnetrygdmottaker b
                | join innlesing i on i.id = b.innlesing_id
                | where b.id = :id""".trimMargin(),
            mapOf<String, Any>(
                "id" to id
            ),
            BarnetrygdmottakerRowMapper()
        ).singleOrNull()
    }

    fun finnAlle(id: InnlesingId): List<Barnetrygdmottaker.Mottatt> {
        return jdbcTemplate.query(
            """select b.*, i.id as innlesing_id, i.år
                | from barnetrygdmottaker b
                | join innlesing i on i.id = b.innlesing_id
                | where i.id = :id""".trimMargin(),
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
    fun finnNesteKlarTilBehandling(innlesingId: InnlesingId, antall: Int): List<UUID> {
        val now = Instant.now(clock).toString()
        println("finnNesteKlarTilBehandling: now=$now")
        return jdbcTemplate.queryForList(
            """ select id 
            | from barnetrygdmottaker
            | where status_type = 'Klar'
            | and innlesing_id = :innlesingId
            | order by id asc
            | fetch first :antall rows only for update skip locked
           """.trimMargin(),
            mapOf(
                "now" to now,
                "innlesingId" to innlesingId.toUUID().toString(),
                "antall" to antall
            ),
            UUID::class.java
        )
    }

    fun finnNesteForRetry(innlesingId: InnlesingId, antall: Int): List<UUID> {
        val now = Instant.now(clock).toString()
        println("finnNesteKlarForRetry: now=$now")
        return jdbcTemplate.queryForList(
            """select id
               | from barnetrygdmottaker
               | where status_type = 'Retry' 
               |and karantene_til < (:now)::timestamptz
               | and karantene_til is not null 
               | and innlesing_id = :innlesingId 
               | order by karantene_til asc 
               | fetch first :antall rows only for update skip locked
           """.trimMargin(),
            mapOf(
                "now" to now,
                "innlesingId" to innlesingId.toUUID().toString(),
                "antall" to antall
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

    fun printTables() {
        jdbcTemplate.query(
            """select id, status_type, statushistorikk
                |from barnetrygdmottaker""".trimMargin(),
            PrintMapper()
        )
    }

    internal class PrintMapper : RowMapper<String> {
        override fun mapRow(rs: ResultSet, rowNum: Int): String {
            for (i in 1..rs.metaData.columnCount) {
                println("${rs.metaData.getColumnName(i)} : ${rs.getObject(i)}")
            }
            return "hello"
        }
    }


    fun oppdaterFeiledeRaderTilKlar(innlesingId: UUID): Int {
        val nyStatus = serialize(Barnetrygdmottaker.Status.Klar())
        return jdbcTemplate.update(
            //language=postgres-psql
            """
            update barnetrygdmottaker 
             set status = '$nyStatus'::jsonb,
             statushistorikk = statushistorikk || '$nyStatus'::jsonb,
             status_type = 'Klar'
             where innlesing_id = '$innlesingId' and status_type = 'Feilet'
        """.trimIndent(),
            emptyMap<String, Any>()
        )
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