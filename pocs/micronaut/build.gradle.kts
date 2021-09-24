plugins {
    id("com.github.johnrengelman.shadow") version "7.0.0"
    id("io.micronaut.application") version "2.0.5"
    id("com.google.cloud.tools.jib") version "3.1.4"
    id("com.github.ben-manes.versions") version "0.39.0"
}

version = "0.1"
group = "com.thoughtworks.recce"

repositories {
    mavenCentral()
}

micronaut {
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("com.thoughtworks.recce.*")
    }
}

dependencies {
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("info.picocli:picocli-codegen")
    annotationProcessor("io.micronaut.data:micronaut-data-processor")
    implementation("info.picocli:picocli")
    implementation("io.micronaut:micronaut-runtime")
    implementation("io.micronaut.data:micronaut-data-jdbc")
    implementation("io.micronaut.picocli:micronaut-picocli")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    implementation("javax.annotation:javax.annotation-api")
    compileOnly("org.projectlombok:lombok")
    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("com.h2database:h2")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.assertj:assertj-db:2.0.2")
    testImplementation("io.micronaut.flyway:micronaut-flyway")
    implementation("io.micronaut:micronaut-validation")

    testImplementation("org.mockito:mockito-core")

}

application {
    mainClass.set("com.thoughtworks.recce.RecceCommand")
}

java {
    sourceCompatibility = JavaVersion.toVersion("16")
    targetCompatibility = JavaVersion.toVersion("16")
}

jib {
    from {
        image = "eclipse-temurin:16-jdk-focal"
    }
    to {
        image = "gcr.io/myapp/jib-image"
    }
}
