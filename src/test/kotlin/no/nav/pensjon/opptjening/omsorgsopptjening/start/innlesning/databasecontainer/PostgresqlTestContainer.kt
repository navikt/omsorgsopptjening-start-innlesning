package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.databasecontainer

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import javax.sql.DataSource


class PostgresqlTestContainer private constructor(image: String) : PostgreSQLContainer<PostgresqlTestContainer>(image) {

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
                        DELETE FROM BARNETRYGDMOTTAKER;
                        DELETE FROM INNLESING;
                    """
            )
            close()
        }
    }

    companion object {
        val instance: PostgresqlTestContainer = createInstance()

        private val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:tc:postgresql:16:///test"
            username = instance.username
            password = instance.password
        })

        fun createInstance(name: String): DataSource {
            val instance = createInstance()
            val dataSource = HikariDataSource(HikariConfig().apply {
                jdbcUrl = "jdbc:tc:postgresql:16:///$name"
                username = instance.username
                password = instance.password
            })
            return dataSource
        }

        private fun createInstance(): PostgresqlTestContainer {
            return PostgresqlTestContainer("postgres:16.4-alpine")
        }
    }
}
