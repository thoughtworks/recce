import com.github.gundy.semver4j.model.Version

plugins {
    val kotlinVersion = "1.6.0"
    id("org.jetbrains.kotlin.jvm") version kotlinVersion
    id("org.jetbrains.kotlin.kapt") version kotlinVersion
    id("org.jetbrains.kotlin.plugin.allopen") version kotlinVersion
    id("org.jetbrains.kotlin.plugin.jpa") version kotlinVersion
    id("io.micronaut.application") version "3.0.1"
    id("com.diffplug.spotless") version "6.0.1"
    id("com.adarshr.test-logger") version "3.1.0"
    id("com.google.cloud.tools.jib") version "3.1.4"
    id("com.github.ben-manes.versions") version "0.39.0"
    id("org.barfuin.gradle.taskinfo") version "1.3.1"
    id("org.ajoberstar.reckon") version "0.13.0"
    jacoco
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
    "micronaut" to "io.micronaut:micronaut-core:3.2.0",
    "restAssured" to "io.rest-assured:rest-assured:4.4.0",

    // Unfortunately not all Mockito or Reactor libs are in the Micronaut BOM, this allows us to keep versions consistent.
    "mockito" to "org.mockito:mockito-core:4.1.0", // Needs to be compatible with Micronaut BOM.
    "reactor" to "io.projectreactor:reactor-core:3.4.12" // Needs to be compatible with Micronaut BOM.
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

configurations.all {
    resolutionStrategy.dependencySubstitution {
        substitute(module("junit:junit"))
            .using(module("io.quarkus:quarkus-junit4-mock:2.5.0.Final"))
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
    implementation("com.google.guava:guava:31.0.1-jre")
    implementation("io.projectreactor:reactor-tools:${depVersions["reactor"]}")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:1.1.5")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("io.github.microutils:kotlin-logging-jvm:2.1.0")
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
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.0.0")
    testImplementation("io.projectreactor:reactor-test:${depVersions["reactor"]}")

    testImplementation("io.rest-assured:rest-assured:${depVersions["restAssured"]}")
    testImplementation("io.rest-assured:kotlin-extensions:${depVersions["restAssured"]}")
    testImplementation("org.awaitility:awaitility-kotlin:4.1.1")

    // Database testing infra
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.postgresql:postgresql")
    testImplementation("org.testcontainers:mysql")
    testRuntimeOnly("mysql:mysql-connector-java")
    testImplementation("org.testcontainers:mssqlserver")
    testRuntimeOnly("com.microsoft.sqlserver:mssql-jdbc")
    testImplementation("org.testcontainers:mariadb")
    testRuntimeOnly("org.mariadb.jdbc:mariadb-java-client")

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

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
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
