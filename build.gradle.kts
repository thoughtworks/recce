plugins {
    val kotlinVersion = "1.6.0-RC2"
    id("org.jetbrains.kotlin.jvm") version kotlinVersion
    id("org.jetbrains.kotlin.kapt") version kotlinVersion
    id("org.jetbrains.kotlin.plugin.allopen") version kotlinVersion
    id("org.jetbrains.kotlin.plugin.jpa") version kotlinVersion
    id("com.github.johnrengelman.shadow") version "7.1.0"
    id("io.micronaut.application") version "2.0.8"
    id("com.diffplug.spotless") version "5.17.1"
    id("com.adarshr.test-logger") version "3.1.0"
    id("com.google.cloud.tools.jib") version "3.1.4"
    id("com.github.ben-manes.versions") version "0.39.0"
    jacoco
}

version = "0.1"
group = "recce.server"

// Workaround to allow dependabot to update versions of libraries together, since dependabot doesn't understand
// the Gradle DSL properly. Here we pick one of the versions where multiple artifacts are released at the same time
// and use this to bump the others consistently.
val depDescriptors = mapOf(
    "micronaut" to "io.micronaut:micronaut-core:3.1.3",
    "exposed" to "org.jetbrains.exposed:exposed-core:0.36.2",
    "restAssured" to "io.rest-assured:rest-assured:4.4.0",
    "mockito" to "org.mockito:mockito-core:4.0.0", // Unfortunately not all Mockito libs are in the Micronaut BOM
)
val depVersions = depDescriptors.mapValues { (_, v) -> v.split(':').last() } + mapOf(
    "javaMajor" to "17",
)

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(depVersions["javaMajor"]!!))
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = depVersions["javaMajor"]!!
    }
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
    implementation("javax.annotation:javax.annotation-api")
    implementation("com.google.guava:guava:31.0.1-jre")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:1.1.5")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("io.github.microutils:kotlin-logging-jvm:2.0.11")
    runtimeOnly("ch.qos.logback:logback-classic")

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
    runtimeOnly("io.r2dbc:r2dbc-pool")
    runtimeOnly("io.r2dbc:r2dbc-postgresql")
    runtimeOnly("io.r2dbc:r2dbc-mssql")
    runtimeOnly("dev.miku:r2dbc-mysql")
    runtimeOnly("org.mariadb:r2dbc-mariadb")
    runtimeOnly("com.oracle.database.r2dbc:oracle-r2dbc")

    testImplementation("org.assertj:assertj-core")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("org.mockito:mockito-core:${depVersions["mockito"]}")
    testImplementation("org.mockito:mockito-inline:${depVersions["mockito"]}")
    testImplementation("org.mockito:mockito-junit-jupiter:${depVersions["mockito"]}")
    testImplementation("org.mockito.kotlin:mockito-kotlin:${depVersions["mockito"]}")
    testImplementation("io.projectreactor:reactor-test:3.4.12")

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
    testRuntimeOnly("com.microsoft.sqlserver:mssql-jdbc")

    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("io.r2dbc:r2dbc-h2")
}

application {
    mainClass.set("recce.server.RecceServerKt")
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

val testExcludeTags: String? by project
tasks.test {
    finalizedBy(tasks.jacocoTestReport)
    useJUnitPlatform {
        excludeTags(testExcludeTags ?: "none")
    }
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
