import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val domeneVersion = "1.0.60"
val springVersion = "3.1.4"
val navTokenSupportVersion = "3.1.7"
val hibernateValidatorVersion = "8.0.1.Final"
val logbackEncoderVersion = "7.4"
val postgresqlVersion = "42.6.0"
val flywayCoreVersion = "9.22.2"
val testcontainersVersion = "1.19.1"
val jacksonVersion = "2.15.2"
val springKafkaTestVersion = "3.0.11"
val azureAdClient = "0.0.7"
val assertjVersion = "3.24.2"
val awaitilityVersion = "4.2.0"
val wiremockVersion = "3.2.0"

plugins {
    kotlin("jvm") version "1.9.10"
    kotlin("plugin.spring") version "1.9.10"
    id("org.springframework.boot") version "3.1.4"
    id("com.github.ben-manes.versions") version "0.49.0"
}

apply(plugin = "io.spring.dependency-management")

group = "no.nav.pensjon.opptjening"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://maven.pkg.github.com/navikt/maven-release") {
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation("no.nav.pensjon.opptjening:omsorgsopptjening-domene-lib:$domeneVersion")

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus:1.11.4")
    implementation("org.springframework:spring-aspects")
    implementation("no.nav.security:token-validation-spring:$navTokenSupportVersion")

    implementation("net.logstash.logback:logstash-logback-encoder:$logbackEncoderVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("no.nav.pensjonopptjening:pensjon-opptjening-azure-ad-client:$azureAdClient")
    implementation("org.hibernate.validator:hibernate-validator:8.0.1.Final")

    // DB
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("org.flywaydb:flyway-core:$flywayCoreVersion")

    // Test - setup
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(kotlin("test"))
    testImplementation("no.nav.security:token-validation-spring-test:$navTokenSupportVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("org.springframework.kafka:spring-kafka-test:$springKafkaTestVersion")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.assertj:assertj-core:$assertjVersion")
    testImplementation("org.awaitility:awaitility:$awaitilityVersion")
    testImplementation("org.wiremock:wiremock:$wiremockVersion")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events(
            TestLogEvent.PASSED,
            TestLogEvent.FAILED,
            TestLogEvent.SKIPPED
        )
    }
}
