package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.feilinfo

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class FeilinfoRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val clock: Clock = Clock.systemUTC()
) {
    fun lagre(feilinfo: Feilinfo) {
        jdbcTemplate.update(
            """insert into feilinfo (time,data) values (:time::timestamptz, :data)""",
            mapOf<String, Any>(
                "time" to feilinfo.time.toString(),
                "data" to feilinfo.data,
            )
        )
    }
}

