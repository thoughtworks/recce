plugins {
    id("org.jetbrains.kotlin.jvm") version "1.5.21"
    id("org.jetbrains.kotlin.kapt") version "1.5.21"
    id("com.github.johnrengelman.shadow") version "7.1.0"
    id("io.micronaut.application") version "2.0.6"
    id("org.jetbrains.kotlin.plugin.allopen") version "1.5.21"
    id("org.jetbrains.kotlin.plugin.jpa") version "1.5.21"
    id("com.google.cloud.tools.jib") version "3.1.4"
    id("com.github.ben-manes.versions") version "0.39.0"
    id("com.diffplug.spotless") version "5.16.0"
}

version = "0.1"
group = "com.thoughtworks.recce.server"

val javaMajorVersion = 16
val kotlinVersion = "1.5.31"
val exposedVersion = "0.35.1"

// Override to get fix for https://github.com/micronaut-projects/micronaut-r2dbc/issues/158 Remove on Micronaut 3.0.4+
val micronautDataVersion = "3.1.0"

repositories {
    mavenCentral()
}

micronaut {
    version("3.0.3")
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("com.thoughtworks.recce.server.*")
    }
}

dependencies {
    kapt("io.micronaut:micronaut-http-validation")
    kapt("io.micronaut.data:micronaut-data-processor")
    implementation("io.micronaut:micronaut-http-client")
    implementation("io.micronaut:micronaut-runtime")
    implementation("io.micronaut:micronaut-validation")
    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    implementation("javax.annotation:javax.annotation-api")
    implementation("io.github.microutils:kotlin-logging-jvm:2.0.11")

    implementation("io.micronaut.flyway:micronaut-flyway")

    implementation("com.google.guava:guava:31.0.1-jre")

    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Traditional JDBC data access
    implementation("io.micronaut.data:micronaut-data-hibernate-jpa")
    implementation("io.micronaut.data:micronaut-data-jdbc")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    runtimeOnly("org.postgresql:postgresql")

    // R2BDC
    implementation("io.micronaut.data:micronaut-data-r2dbc:$micronautDataVersion")
    implementation("io.micronaut.r2dbc:micronaut-r2dbc-core")
    runtimeOnly("io.r2dbc:r2dbc-postgresql")

    testImplementation("org.assertj:assertj-core")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("org.mockito:mockito-inline:4.0.0")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("org.mockito.kotlin:mockito-kotlin:3.2.0")
    testImplementation("io.projectreactor:reactor-test:3.4.10")

    testImplementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    testImplementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")

    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("io.r2dbc:r2dbc-h2")
}

application {
    mainClass.set("com.thoughtworks.recce.server.ApplicationKt")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaMajorVersion))
    }
}

tasks {
    compileKotlin {
        kotlinOptions {
            jvmTarget = "$javaMajorVersion"
        }
    }
    compileTestKotlin {
        kotlinOptions {
            jvmTarget = "$javaMajorVersion"
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

jib {
    from {
        image = "eclipse-temurin:$javaMajorVersion-jdk-alpine"
    }
    to {
        image = "recce/recce-server"
    }
}
