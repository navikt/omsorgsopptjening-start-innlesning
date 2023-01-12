import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val springVersion = "3.0.0"
val navTokenSupportVersion = "3.0.2"
val hibernateValidatorVersion = "8.0.0.Final"
val logbackEncoderVersion = "7.2"
val postgresqlVersion = "42.5.1"
val flywayCoreVersion = "9.11.0"
val testcontainersVersion = "1.17.6"
val jacksonVersion = "2.14.1"

plugins {
    kotlin("jvm") version "1.8.0"
    kotlin("plugin.spring") version "1.8.0"
    id("org.springframework.boot") version "3.0.1"
}

apply(plugin = "io.spring.dependency-management")

group = "no.nav.pensjon.opptjening"
java.sourceCompatibility = JavaVersion.VERSION_17

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("no.nav.security:token-validation-spring:$navTokenSupportVersion")

    implementation("org.hibernate:hibernate-validator:$hibernateValidatorVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logbackEncoderVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // DB
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("org.flywaydb:flyway-core:$flywayCoreVersion")

    // Test - setup
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.cloud:spring-cloud-starter-contract-stub-runner:4.0.0")
    testImplementation(kotlin("test"))
    testImplementation("no.nav.security:token-validation-spring-test:$navTokenSupportVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")

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