package no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning

import no.nav.pensjon.opptjening.omsorgsopptjening.felles.domene.kafka.Topics
import no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.barnetrygd.kafka.BarnetrygdmottakerKafkaTopic
import no.nav.security.token.support.spring.test.MockLoginController
import no.nav.security.token.support.spring.test.MockOAuth2ServerAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker

/**
 * Lokal smoke-run: booter HELE applikasjonskonteksten (Flyway, alle beans, Kafka-lytteren og de
 * profil-gatede bakgrunnstaskene) mot en in-process Kafka-broker og en Testcontainers Postgres
 * (via jdbc:tc i src/test/resources/application.yml). Gjenbruker `kafkaIntegrationTest`-profilen
 * som allerede gir PLAINTEXT Kafka, mock OAuth2 og mock token-providers - ingen ekstern infra kreves.
 *
 * Kjør:  ./gradlew runLocal
 * Sjekk: http://localhost:8080/actuator/health  ->  {"status":"UP"}
 *
 * Appen idler etter oppstart (ingen meldinger i køen), så eksterne HTTP-kall (PDL/barnetrygd/hjelpestønad)
 * skjer ikke. Dette verifiserer at wiringen etter Spring Boot 4-migreringen faktisk starter.
 * Throwaway dev-launcher i test-scope, ikke prod-kode.
 */
fun main(args: Array<String>) {
    val broker = EmbeddedKafkaKraftBroker(
        1, 1,
        BarnetrygdmottakerKafkaTopic.NAME,
        Topics.Omsorgsopptjening.NAME,
    )
    broker.afterPropertiesSet()
    // System-property vinner over application.yml, så kafka.brokers-plassholderen (${spring.embedded...})
    // i test-yml-en blir aldri evaluert.
    System.setProperty("kafka.brokers", broker.brokersAsString)

    // Importerer mock-OAuth2-autoconfig direkte (ikke via @EnableMockOAuth2Server på en
    // @Configuration - det ville blitt component-scannet og brutt alle @SpringBootTest-kontekster).
    SpringApplicationBuilder(
        Application::class.java,
        MockOAuth2ServerAutoConfiguration::class.java,
        MockLoginController::class.java,
    )
        .profiles("kafkaIntegrationTest")
        .run(*args)
}
