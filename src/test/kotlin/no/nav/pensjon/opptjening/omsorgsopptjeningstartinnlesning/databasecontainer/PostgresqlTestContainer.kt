package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.databasecontainer

import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait


class PostgresqlTestContainer private constructor() : PostgreSQLContainer<PostgresqlTestContainer>(
    IMAGE_VERSION
) {
    override fun start() {
        super.start()
        super.waitingFor(Wait.defaultWaitStrategy())
    }

    override fun stop() {
        //do nothing, JVM handles shut down
    }

    companion object {
        private const val IMAGE_VERSION = "postgres:14.4"
        private var container: PostgresqlTestContainer? = null
        val instance: PostgresqlTestContainer
            get() {
                if (container == null) container = PostgresqlTestContainer()
                return container!!
            }
    }
}