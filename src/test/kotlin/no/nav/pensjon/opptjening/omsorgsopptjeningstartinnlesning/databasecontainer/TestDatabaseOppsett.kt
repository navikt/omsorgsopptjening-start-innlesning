package no.nav.pensjon.opptjening.omsorgsopptjeningstartinnlesning.databasecontainer

import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class TestDatabaseOppsett {
    companion object {
        init {
            PostgreSQLContainer14().also {
                it.start()
                it.waitingFor(Wait.defaultWaitStrategy())
            }
        }
    }
}

private class PostgreSQLContainer14 : PostgreSQLContainer<PostgreSQLContainer14>("postgres:14.4")