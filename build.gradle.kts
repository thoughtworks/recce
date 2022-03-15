@file:Suppress("GradlePackageUpdate")

import com.github.gundy.semver4j.model.Version
import java.util.*

plugins {
    val kotlinVersion = "1.6.10"
    id("org.jetbrains.kotlin.jvm") version kotlinVersion
    id("org.jetbrains.kotlin.kapt") version kotlinVersion
    id("org.jetbrains.kotlin.plugin.allopen") version kotlinVersion
    id("org.jetbrains.kotlin.plugin.jpa") version kotlinVersion
    id("io.micronaut.application") version "3.3.0"
    id("com.diffplug.spotless") version "6.3.0"
    jacoco
    id("com.adarshr.test-logger") version "3.2.0"
    id("com.google.cloud.tools.jib") version "3.2.0"
    id("com.github.ben-manes.versions") version "0.42.0"
    id("org.barfuin.gradle.taskinfo") version "1.3.1"
    id("org.ajoberstar.reckon") version "0.16.1"
    id("org.owasp.dependencycheck") version "6.5.3"
}

group = "recce.server"

reckon {
    scopeFromProp()
    stageFromProp("dev", "final")
}

// Workaround to allow dependabot to update versions of libraries together, since dependabot doesn't understand
// the Gradle DSL properly. Here we pick one of the versions where multiple artifacts are released at the same time
// and use this to bump the others consistently.
val depDescriptors = mapOf(
    "micronaut" to "io.micronaut:micronaut-core:3.3.4",
    "restAssured" to "io.rest-assured:rest-assured:4.5.1",

    // Unfortunately not all Mockito/Reactor libs are in the Micronaut BOM, this allows us to keep versions consistent.
    "mockito" to "org.mockito:mockito-core:4.4.0", // Needs to be compatible with Micronaut BOM.
    "reactor" to "io.projectreactor:reactor-core:3.4.16", // Needs to be compatible with Micronaut BOM.
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
        allWarningsAsErrors = true
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

kapt {
    arguments {
        val props = mapOf(
            "rapidoc.enabled" to true,
            "rapidoc.theme" to "dark",
            "rapidoc.render-style" to "view",
        )
        arg("micronaut.openapi.views.spec", props.entries.joinToString(",") { "${it.key}=${it.value}" })
    }
}

configurations.all {
    resolutionStrategy.dependencySubstitution {
        substitute(module("junit:junit"))
            .using(module("io.quarkus:quarkus-junit4-mock:2.7.4.Final"))
            .because(
                "We don't want JUnit 4; but is an unneeded transitive of testcontainers. " +
                    "See https://github.com/testcontainers/testcontainers-java/issues/970"
            )
    }
}

dependencies {
    kapt("io.micronaut:micronaut-http-validation")
    kapt("io.micronaut.data:micronaut-data-processor")
    implementation("io.micronaut:micronaut-runtime")
    implementation("io.micronaut:micronaut-validation")
    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
    implementation("javax.annotation:javax.annotation-api")
    implementation("com.google.guava:guava:31.1-jre")
    implementation("io.projectreactor:reactor-tools:${depVersions["reactor"]}")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:1.1.5")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("io.github.microutils:kotlin-logging-jvm:2.1.21")
    runtimeOnly("ch.qos.logback:logback-classic")

    // Version locked due to https://github.com/micronaut-projects/micronaut-openapi/issues/656, should be fixed after Micronaut OpenAPI 4.0.0
    kapt("io.micronaut.openapi:micronaut-openapi:3.2.0!!")
    implementation("io.swagger.core.v3:swagger-annotations")

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
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.0.0")
    testImplementation("io.projectreactor:reactor-test:${depVersions["reactor"]}")

    testImplementation("io.rest-assured:rest-assured:${depVersions["restAssured"]}")
    testImplementation("io.rest-assured:kotlin-extensions:${depVersions["restAssured"]}")
    testImplementation("org.awaitility:awaitility-kotlin:4.2.0")

    // Database testing infra
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.postgresql:postgresql")
    testImplementation("org.testcontainers:mysql")
    testRuntimeOnly("mysql:mysql-connector-java")
    testRuntimeOnly("org.flywaydb:flyway-mysql")
    testImplementation("org.testcontainers:mssqlserver")
    testRuntimeOnly("com.microsoft.sqlserver:mssql-jdbc")
    testRuntimeOnly("org.flywaydb:flyway-sqlserver")
    testImplementation("org.testcontainers:mariadb")
    testRuntimeOnly("org.mariadb.jdbc:mariadb-java-client")

    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("io.r2dbc:r2dbc-h2")
}

dependencyCheck {
    suppressionFile = "build-config/dependency-check-suppressions.xml"
    skipTestGroups = false
    // The kapt configurations cause false positives for some reason. See https://github.com/dependency-check/dependency-check-gradle/issues/239
    skipConfigurations = listOf("_classStructurekaptKotlin", "_classStructurekaptTestKotlin")
    analyzers.assemblyEnabled = false // Unneeded, and creares warning noise
}

application {
    mainClass.set("recce.server.RecceServer")
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

tasks.register<Test>("slowTests") {
    group = "verification"
    useJUnitPlatform {
        includeTags("slow")
    }
}

tasks.register<Test>("fastTests") {
    group = "verification"
    useJUnitPlatform {
        excludeTags("slow")
    }
}

val githubRepoOwner = "thoughtworks-sea"
val containerRepoName = "recce-server"
jib {
    from {
        image = "eclipse-temurin:${depVersions["javaMajor"]}-jdk-alpine"
    }
    to {
        val fullVersion = Version.fromString(project.version.toString())
        val tagVersion = Version.builder()
            .major(fullVersion.major)
            .minor(fullVersion.minor)
            .patch(fullVersion.patch)
            .preReleaseIdentifiers(fullVersion.preReleaseIdentifiers.filterIndexed { i, _ -> i == 0 })
            .build()
        image = "ghcr.io/$githubRepoOwner/$containerRepoName"
        tags = setOf(tagVersion.toString(), "latest")
    }
    container {
        creationTime = "USE_CURRENT_TIMESTAMP"
        labels.set(mapOf("org.opencontainers.image.source" to "https://github.com/$githubRepoOwner/recce"))
    }
}

// Jib task pushes an image. Only do so after running all checks
tasks.jib.configure {
    dependsOn(tasks.check)
}

// use different naming when building locally, to avoid confusion
tasks.jibDockerBuild.configure {
    jib {
        to {
            image = containerRepoName
        }
    }
}

tasks.register("generateVersionProperties") {
    doLast {
        file("$buildDir/resources/main/build-info.properties").writer().use {
            Properties().apply {
                setProperty("version", "$version")
                store(it, null)
            }
        }
    }
}

tasks.processResources.configure {
    dependsOn("generateVersionProperties")
}

tasks.run.configure {
    doFirst { environment("version", "$version") }
}
