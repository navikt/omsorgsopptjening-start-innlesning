import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.plugin.SpringBootPlugin

val domeneVersion = "2.1.103"
val azureAdClient = "0.0.7"
val logbackEncoderVersion = "9.0"
val flywayCoreVersion = "12.6.0"
val wiremockVersion = "3.13.1"
val mockitoVersion = "6.3.0"
val unleashVersion = "12.2.3"
val navTokenSupportVersion = "6.0.8"

plugins {
    val kotlinVersion = "2.3.21"
    id("org.jetbrains.kotlin.jvm") version kotlinVersion
    id("org.jetbrains.kotlin.plugin.spring") version kotlinVersion
    id("org.springframework.boot") version "4.1.0"
    id("com.github.ben-manes.versions") version "0.54.0"
}

group = "no.nav.pensjon.opptjening"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://maven.pkg.github.com/navikt/maven-release") {
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    // Native Gradle BOM-import (erstatter io.spring.dependency-management-pluginet). Constraints fra
    // spring-boot-dependencies gjelder transitivt til testImplementation (arver fra implementation).
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework:spring-aspects") // aspectjweaver, kreves av @EnableRetry sin AspectJ-autoproxy
    implementation("org.springframework.retry:spring-retry:2.0.13") // ikke i Boot 4 BOM, versjon må settes
    implementation("org.springframework.boot:spring-boot-starter-jdbc") // ren JdbcTemplate, ingen Spring Data-repos
    implementation("io.getunleash:unleash-client-java:$unleashVersion")
    implementation("no.nav.security:token-validation-spring:$navTokenSupportVersion")
    implementation("no.nav.security:token-client-spring:$navTokenSupportVersion")
    implementation("org.hibernate.validator:hibernate-validator") // jakarta.validation-provider for @Validated @ConfigurationProperties (version fra BOM)

    // Apache HttpClient 5 for connection pool management (version managed by BOM)
    implementation("org.apache.httpcomponents.client5:httpclient5")

    // Internal libraries
    implementation("no.nav.pensjon.opptjening:omsorgsopptjening-domene-lib:$domeneVersion")
    implementation("no.nav.pensjonopptjening:pensjon-opptjening-azure-ad-client:$azureAdClient")
    implementation("com.google.guava:guava:33.5.0-jre")

    // Jackson 3 (tools.jackson.*): core/annotations/databind kommer transitivt via startere (styres av Spring Boot BOM, jackson-bom 3.1.4).
    // java.time-støtte er innebygd i databind i Jackson 3 - jackson-datatype-jsr310 trengs ikke.
    implementation("tools.jackson.module:jackson-module-kotlin") // kode bruker readValue/jacksonObjectMapper direkte

    // Log and metric
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("net.logstash.logback:logstash-logback-encoder:$logbackEncoderVersion")

    // DB (postgresql version managed by BOM)
    implementation("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayCoreVersion")

    // Test
    testImplementation(kotlin("test"))
    testImplementation("org.springframework.boot:spring-boot-starter-test") // includes assertj, jsonassert, mockito
    testImplementation("org.springframework.boot:spring-boot-webmvc-test") // Boot 4: MockMvc-autokonfig + @AutoConfigureMockMvc (flyttet ut av starter-web)
    testImplementation("org.testcontainers:postgresql:1.21.4") // TC 1.x jdbc:tc-driver, som i afp-api
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:$mockitoVersion")
    testImplementation("org.wiremock:wiremock-standalone:$wiremockVersion") // shaded egen Jetty; immun mot Boot 4 BOM sin Jetty-versjon (unngår både NoSuchMethodError på jetty12 og keep-alive POST-bug i 4.0.0-beta)
    testImplementation("no.nav.security:token-validation-spring-test:$navTokenSupportVersion")
    testImplementation("net.javacrumbs.json-unit:json-unit-assertj:5.0.0")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget = JvmTarget.JVM_25
    }
}

tasks.withType<Test> {
    maxParallelForks = 1 // Shared resources (db/wiremock)
    useJUnitPlatform()
    testLogging {
        events(
            TestLogEvent.PASSED,
            TestLogEvent.FAILED,
            TestLogEvent.SKIPPED
        )
    }
}

tasks.withType<DependencyUpdatesTask>().configureEach {
    rejectVersionIf {
        isNonStableVersion(candidate.version)
    }
}

tasks.register<JavaExec>("runLocal") {
    group = "application"
    description = "Starter appen lokalt (kafkaIntegrationTest-profil, ingen ekstern infra)"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("no.nav.pensjon.opptjening.omsorgsopptjening.start.innlesning.LocalRunKt")
}

fun isNonStableVersion(version: String): Boolean {
    return listOf("BETA", "RC", "-M", ".CR").any { version.uppercase().contains(it) }
}
