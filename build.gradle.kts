import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val domeneVersion = "1.0.69"
val navTokenSupportVersion = "4.1.8"
val hibernateValidatorVersion = "8.0.1.Final"
val logbackEncoderVersion = "7.4"
val postgresqlVersion = "42.7.3"
val flywayCoreVersion = "9.22.3"
val testcontainersVersion = "1.19.8"
val jacksonVersion = "2.17.1"
val springKafkaTestVersion = "3.2.0"
val azureAdClient = "0.0.7"
val assertjVersion = "3.26.0"
val awaitilityVersion = "4.2.1"
val wiremockVersion = "3.6.0"
val micrometerRegistryPrometheusVersion = "1.13.0"
val mockitoKotlinVersion = "5.3.1"
val unleashVersion = "9.2.2"

val snappyJavaVersion = "1.1.10.5"
val snakeYamlVersion = "2.2"

plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.spring") version "2.0.0"
    id("org.springframework.boot") version "3.3.0"
    id("com.github.ben-manes.versions") version "0.51.0"
}

apply(plugin = "io.spring.dependency-management")

group = "no.nav.pensjon.opptjening"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
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
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerRegistryPrometheusVersion")
    implementation("org.springframework:spring-aspects")
    implementation("no.nav.security:token-validation-spring:$navTokenSupportVersion")

    implementation("net.logstash.logback:logstash-logback-encoder:$logbackEncoderVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("no.nav.pensjonopptjening:pensjon-opptjening-azure-ad-client:$azureAdClient")
    implementation("org.hibernate.validator:hibernate-validator:$hibernateValidatorVersion")
    implementation("io.getunleash:unleash-client-java:$unleashVersion")

    // DB
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("org.flywaydb:flyway-core:$flywayCoreVersion")

    // These are transitive dependencies, but overriding them on top level due to vulnerabilities
    // (and in some cases, the wrong version being picked)
    implementation("org.xerial.snappy:snappy-java:$snappyJavaVersion")
    implementation("org.yaml:snakeyaml:$snakeYamlVersion")

    // Test - setup
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(kotlin("test"))
    testImplementation("no.nav.security:token-validation-spring-test:$navTokenSupportVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("org.springframework.kafka:spring-kafka-test:$springKafkaTestVersion")
    testImplementation("org.mockito.kotlin:mockito-kotlin:$mockitoKotlinVersion")
    testImplementation("org.assertj:assertj-core:$assertjVersion")
    testImplementation("org.awaitility:awaitility:$awaitilityVersion")
    testImplementation("org.wiremock:wiremock-jetty12:$wiremockVersion")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "21"
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