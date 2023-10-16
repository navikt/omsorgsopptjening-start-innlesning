package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.databasecontainer

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import javax.sql.DataSource


class PostgresqlTestContainer2 private constructor(image: String) : PostgreSQLContainer<PostgresqlTestContainer2>(image) {

    init {
        start()
    }

    override fun start() {
        super.start()
        super.waitingFor(Wait.defaultWaitStrategy())
    }

    override fun stop() {
        //Do nothing, JVM handles shut down
    }

    fun removeDataFromDB() {
        dataSource.connection.apply {
            createStatement().execute(
                """
                        DELETE FROM BARNETRYGDMOTTAKER_STATUS;
                        DELETE FROM BARNETRYGDMOTTAKER;
                    """
            )
            close()
        }
    }

    companion object {
        private val instance: PostgresqlTestContainer2 = PostgresqlTestContainer2("postgres:14.7-alpine")
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:tc:postgresql:14:///testmonitoring"
            username = instance.username
            password = instance.password
        })
    }
}