plugins {
    id("org.jetbrains.kotlin.jvm") version "1.5.21"
    id("org.jetbrains.kotlin.kapt") version "1.5.21"
    id("com.github.johnrengelman.shadow") version "7.0.0"
    id("io.micronaut.application") version "2.0.6"
    id("org.jetbrains.kotlin.plugin.allopen") version "1.5.21"
    id("org.jetbrains.kotlin.plugin.jpa") version "1.5.21"
    id("com.google.cloud.tools.jib") version "3.1.4"
    id("com.github.ben-manes.versions") version "0.39.0"
    id("com.diffplug.spotless") version "5.15.2"
}

version = "0.1"
group = "com.thoughtworks.recce.server"

val kotlinVersion = project.properties.get("kotlinVersion")
repositories {
    mavenCentral()
}

micronaut {
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
    implementation("io.micronaut.flyway:micronaut-flyway")
    implementation("io.micronaut.data:micronaut-data-hibernate-jpa")
    implementation("io.micronaut.data:micronaut-data-jdbc")
    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    implementation("javax.annotation:javax.annotation-api")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")

    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.fasterxml.jackson.module:jackson-module-kotlin")

    testImplementation("org.assertj:assertj-core")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.mockito:mockito-core")

    testRuntimeOnly("com.h2database:h2")
}

application {
    mainClass.set("com.thoughtworks.recce.server.ApplicationKt")
}

java {
    sourceCompatibility = JavaVersion.toVersion("16")
}

tasks {
    compileKotlin {
        kotlinOptions {
            jvmTarget = "16"
        }
    }
    compileTestKotlin {
        kotlinOptions {
            jvmTarget = "16"
        }
    }
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    // configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    kotlin {
        ktlint()
    }
    kotlinGradle {
        ktlint()
    }
}

jib {
    from {
        image = "eclipse-temurin:16-jdk-focal"
    }
    to {
        image = "recce/recce-server"
    }
}
