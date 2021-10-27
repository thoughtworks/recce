plugins {
    id("org.jetbrains.kotlin.jvm") version "1.5.21"
    id("org.jetbrains.kotlin.kapt") version "1.5.21"
    id("com.github.johnrengelman.shadow") version "7.1.0"
    id("io.micronaut.application") version "2.0.8"
    id("org.jetbrains.kotlin.plugin.allopen") version "1.5.21"
    id("org.jetbrains.kotlin.plugin.jpa") version "1.5.21"
    id("com.google.cloud.tools.jib") version "3.1.4"
    id("com.github.ben-manes.versions") version "0.39.0"
    id("com.diffplug.spotless") version "5.17.0"
    id("com.adarshr.test-logger") version "3.0.0"
    jacoco
}

version = "0.1"
group = "recce.server"

// Workaround to allow dependabot to update the micronaut version, since dependabot
// doesn't understand the micronaut plugin DSL
val depDescriptors = mapOf(
    "micronaut" to "io.micronaut:micronaut-core:3.1.1",
    "exposed" to "org.jetbrains.exposed:exposed-core:0.36.1",
    "restAssured" to "io.rest-assured:rest-assured:4.4.0",
)
val depVersions = depDescriptors.mapValues { (_, v) -> v.split(':').last() } + mapOf(
    "javaMajor" to "16",
    "kotlin" to "1.5.31",
)

repositories {
    mavenCentral()
}

micronaut {
    version(depVersions["micronaut"])
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("recce.server.*")
    }
}

dependencies {
    kapt("io.micronaut:micronaut-http-validation")
    kapt("io.micronaut.data:micronaut-data-processor")
    implementation("io.micronaut:micronaut-runtime")
    implementation("io.micronaut:micronaut-validation")
    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
    implementation("org.jetbrains.kotlin:kotlin-reflect:${depVersions["kotlin"]}")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${depVersions["kotlin"]}")
    implementation("javax.annotation:javax.annotation-api")
    implementation("io.github.microutils:kotlin-logging-jvm:2.0.11")
    implementation("com.google.guava:guava:31.0.1-jre")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:1.1.5")

    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Core persistence support with Micronaut Data
    compileOnly("jakarta.persistence:jakarta.persistence-api:2.2.3")

    // Traditional JDBC data access (for rec DB)
    implementation("io.micronaut.flyway:micronaut-flyway")
    implementation("io.micronaut.data:micronaut-data-jdbc")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    runtimeOnly("org.postgresql:postgresql")

    // R2BDC data access (for use by all data sources)
    implementation("io.micronaut.data:micronaut-data-r2dbc")
    implementation("io.micronaut.r2dbc:micronaut-r2dbc-core")
    implementation("io.r2dbc:r2dbc-pool:0.8.7.RELEASE")
    runtimeOnly("io.r2dbc:r2dbc-postgresql")
    runtimeOnly("io.r2dbc:r2dbc-mssql")
    runtimeOnly("dev.miku:r2dbc-mysql")

    testImplementation("org.assertj:assertj-core")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito:mockito-inline:4.0.0")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.0.0")
    testImplementation("io.projectreactor:reactor-test:3.4.11")

    testImplementation("org.jetbrains.exposed:exposed-core:${depVersions["exposed"]}")
    testImplementation("org.jetbrains.exposed:exposed-jdbc:${depVersions["exposed"]}")

    testImplementation("io.rest-assured:rest-assured:${depVersions["restAssured"]}")
    testImplementation("io.rest-assured:kotlin-extensions:${depVersions["restAssured"]}")

    // Database testing infra
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.postgresql:postgresql")
    testImplementation("org.testcontainers:mysql")
    testRuntimeOnly("mysql:mysql-connector-java")
    testImplementation("org.testcontainers:mssqlserver")
    testRuntimeOnly("com.microsoft.sqlserver:mssql-jdbc:9.4.0.jre16")

    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("io.r2dbc:r2dbc-h2")
}

application {
    mainClass.set("recce.server.RecceServerKt")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(depVersions["javaMajor"]!!))
    }
}

tasks {
    compileKotlin {
        kotlinOptions {
            jvmTarget = depVersions["javaMajor"]!!
        }
    }
    compileTestKotlin {
        kotlinOptions {
            jvmTarget = depVersions["javaMajor"]!!
        }
    }
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    val editorConfig = mapOf("disabled_rules" to "no-wildcard-imports")
    kotlin {
        ktlint().userData(editorConfig)
    }
    kotlinGradle {
        ktlint().userData(editorConfig)
    }
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
}

jib {
    from {
        image = "eclipse-temurin:${depVersions["javaMajor"]}-jdk-alpine"
    }
    to {
        image = "recce/recce-server"
    }
}
