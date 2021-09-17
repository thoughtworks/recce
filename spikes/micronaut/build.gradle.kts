plugins {
    id("com.github.johnrengelman.shadow") version "7.0.0"
    id("io.micronaut.application") version "2.0.4"
    id("com.google.cloud.tools.jib") version "2.8.0"
}

version = "0.1"
group = "com.thoughtworks.datarec"

repositories {
    mavenCentral()
}

micronaut {
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("com.thoughtworks.datarec.*")
    }
}

dependencies {
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("info.picocli:picocli-codegen")
    annotationProcessor("io.micronaut.data:micronaut-data-processor")
    implementation("info.picocli:picocli")
    implementation("io.micronaut:micronaut-runtime")
    implementation("io.micronaut.data:micronaut-data-jdbc")
    implementation("io.micronaut.flyway:micronaut-flyway")
    implementation("io.micronaut.picocli:micronaut-picocli")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    implementation("javax.annotation:javax.annotation-api")
    compileOnly("org.projectlombok:lombok")
    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("com.h2database:h2")
    testImplementation("org.assertj:assertj-core")
    implementation("io.micronaut:micronaut-validation")

    testImplementation("org.mockito:mockito-core")

}


application {
    mainClass.set("com.thoughtworks.datarec.DatarecCommand")
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
