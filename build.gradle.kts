@file:Suppress("GradlePackageUpdate")

plugins {
    val kotlinVersion = "1.9.20"
    id("org.jetbrains.kotlin.jvm") version kotlinVersion
    id("org.jetbrains.kotlin.kapt") version kotlinVersion
    id("org.jetbrains.kotlin.plugin.allopen") version kotlinVersion
    id("org.jetbrains.kotlin.plugin.jpa") version kotlinVersion
    id("io.micronaut.application") version "3.7.10"
    id("com.diffplug.spotless") version "6.22.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.3"
    id("com.github.spotbugs") version "5.2.3"
    jacoco
    id("com.adarshr.test-logger") version "4.0.0"
    id("com.google.cloud.tools.jib") version "3.4.0"
    id("org.owasp.dependencycheck") version "8.4.2"
}

group = "recce.server"

// Workaround to allow dependabot to update versions of libraries together, since dependabot doesn't understand
// the Gradle DSL properly. Here we pick one of the versions where multiple artifacts are released at the same time
// and use this to bump the others consistently.
val depDescriptors =
    mapOf(
        "micronaut" to "io.micronaut:micronaut-core:3.10.3",
        "restAssured" to "io.rest-assured:rest-assured:4.5.1"
    )
val depVersions =
    depDescriptors.mapValues { (_, v) -> v.split(':').last() } +
        mapOf(
            "javaMajor" to "17",
            "reactorToolsVersionExpected" to "3.5.11"
        )

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(depVersions["javaMajor"]!!.toInt())
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
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
        val props = mapOf("rapidoc.enabled" to true, "rapidoc.theme" to "dark", "rapidoc.render-style" to "view")
        arg("micronaut.openapi.views.spec", props.entries.joinToString(",") { "${it.key}=${it.value}" })
    }
}

configurations.all {
    resolutionStrategy.dependencySubstitution {
        substitute(module("junit:junit"))
            .using(module("io.quarkus:quarkus-junit4-mock:3.0.0.Final"))
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
    implementation("io.micronaut.security:micronaut-security-jwt")
    implementation("javax.annotation:javax.annotation-api")
    implementation("com.google.guava:guava:32.1.3-jre") {
        // see https://github.com/google/guava/pull/6606
        exclude(module = "error_prone_annotations")
        exclude(module = "checker-qual")
        exclude(module = "jsr305")
    }
    implementation("io.projectreactor:reactor-tools")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("io.github.microutils:kotlin-logging-jvm:2.1.23")
    runtimeOnly("ch.qos.logback:logback-classic")

    // Needs to be compatible with Micronaut's reactor-based BOM.
    implementation(platform("io.projectreactor:reactor-bom:2023.0.0"))

    // OpenAPI specification and interactive UI generated from code
    kapt("io.micronaut.openapi:micronaut-openapi")
    implementation("io.swagger.core.v3:swagger-annotations")

    // Core persistence support with Micronaut Data
    compileOnly("jakarta.persistence:jakarta.persistence-api:3.0.0")

    // Traditional JDBC data access (for rec DB)
    implementation("io.micronaut.flyway:micronaut-flyway")
    implementation("io.micronaut.data:micronaut-data-jdbc")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    runtimeOnly("org.postgresql:postgresql")

    // R2BDC data access (for use by all data sources)
    implementation("io.micronaut.data:micronaut-data-r2dbc")
    implementation("io.micronaut.r2dbc:micronaut-r2dbc-core")
    runtimeOnly("io.r2dbc:r2dbc-pool")
    runtimeOnly("org.postgresql:r2dbc-postgresql")
    runtimeOnly("io.r2dbc:r2dbc-mssql")
    runtimeOnly("dev.miku:r2dbc-mysql")
    runtimeOnly("org.mariadb:r2dbc-mariadb")
    runtimeOnly("com.oracle.database.r2dbc:oracle-r2dbc")

    testImplementation("org.assertj:assertj-core")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("io.projectreactor:reactor-test")

    testImplementation(platform("org.mockito:mockito-bom:5.7.0")) // Needs to be compatible with Micronaut BOM
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")

    testImplementation("io.rest-assured:rest-assured:${depVersions["restAssured"]}")
    testImplementation("io.rest-assured:kotlin-extensions:${depVersions["restAssured"]}")
    testImplementation("org.awaitility:awaitility-kotlin:4.2.0")

    // Database testing infra
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")

    testImplementation("org.testcontainers:mysql")
    testRuntimeOnly("mysql:mysql-connector-java:8.0.33") { // Remove fixed version when micronaut has updated to 8.0.33+
        exclude("com.google.protobuf", "protobuf-java") // Unnecessary, we don't use this part of the driver
    }
    testRuntimeOnly("org.flywaydb:flyway-mysql")

    testImplementation("org.testcontainers:mssqlserver")
    testRuntimeOnly("com.microsoft.sqlserver:mssql-jdbc")
    testRuntimeOnly("org.flywaydb:flyway-sqlserver")

    testImplementation("org.testcontainers:mariadb")
    testRuntimeOnly("org.mariadb.jdbc:mariadb-java-client")

    testRuntimeOnly("com.h2database:h2")
    // Remove version number and excludes block when Micronaut has updated to at least 1.0.0.RELEASE
    testRuntimeOnly("io.r2dbc:r2dbc-h2:1.0.0.RELEASE") {
        exclude("io.projectreactor", "reactor-core")
    }

    spotbugs("com.github.spotbugs:spotbugs:4.8.1")
    spotbugsPlugins("com.h3xstream.findsecbugs:findsecbugs-plugin:1.12.0")
}

dependencyCheck {
    failBuildOnCVSS = 1f
    suppressionFile = "build-config/dependency-check-suppressions.xml"
    scanConfigurations = listOf("runtimeClasspath")
    analyzers.assemblyEnabled = false // Unneeded, and creates warning noise
}

application {
    mainClass.set("recce.server.RecceServer")
}

tasks.run.configure {
    doFirst { environment("version", "$version") }

    // Workaround https://github.com/thoughtworks-sea/recce/issues/155
    jvmArgs("-XX:+StartAttachListener")
    environment.computeIfAbsent("AUTH_USERNAME") { "admin" }
    environment.computeIfAbsent("AUTH_PASSWORD") { "admin" }
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    kotlin {
        ktlint()
    }
    kotlinGradle {
        ktlint()
    }
}

detekt {
    config.from(files("build-config/detekt.yml"))
    buildUponDefaultConfig = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        xml.required.set(false)
        txt.required.set(false)
        sarif.required.set(false)
    }
}

spotbugs {
    excludeFilter.set(file("build-config/spotbugs-exclude.xml"))
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports.create("html") {
        required.set(true)
        outputLocation.set(file("$buildDir/reports/${this@configureEach.name}.html"))
        setStylesheet("fancy-hist.xsl")
    }
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports.xml.required.set(true)
}

tasks.register("cleanLeaveBuildDir") {
    doLast {
        project.delete(files("$buildDir/*"))
    }
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
        image = "eclipse-temurin:${depVersions["javaMajor"]}-jre"
        platforms {
            platform {
                architecture = "amd64"
                os = "linux"
            }
            platform {
                architecture = "arm64"
                os = "linux"
            }
        }
    }
    to {
        val fullVersion = com.github.zafarkhaja.semver.Version.valueOf(project.version.toString())
        val tagVersion =
            com.github.zafarkhaja.semver.Version.Builder()
                .setNormalVersion(fullVersion.normalVersion)
                .setPreReleaseVersion(fullVersion.preReleaseVersion.split('.')[0])
                .build()
        tags = setOf(tagVersion.toString(), "latest")
    }
    container {
        user = "1000:1000"
        ports = listOf("8080")
        environment = mapOf("version" to version.toString())
        labels.set(mapOf("org.opencontainers.image.source" to "https://github.com/$githubRepoOwner/recce"))
        jvmFlags = listOf("-javaagent:/app/libs/reactor-tools-${depVersions["reactorToolsVersionExpected"]}.jar")
    }
}

val checkJibDependencies =
    tasks.register("checkJibDependencies") {
        doFirst {
            val resolvedReactorToolsVersion =
                project.configurations
                    .runtimeClasspath
                    .get()
                    .resolvedConfiguration
                    .resolvedArtifacts.find { it.name == "reactor-tools" }?.moduleVersion?.id?.version
            if (depVersions["reactorToolsVersionExpected"] != resolvedReactorToolsVersion) {
                throw GradleException(
                    "Jib docker build expected reactor-tools [${depVersions["reactorToolsVersionExpected"]}] but " +
                        "found [$resolvedReactorToolsVersion] in dependencies. Update reactorToolsVersionExpected!"
                )
            }
        }
    }

// Jib task pushes an image. Only do so after running all checks
tasks.register<com.google.cloud.tools.jib.gradle.BuildImageTask>("jibGitHubContainerRegistry") {
    dependsOn(checkJibDependencies)
    dependsOn(tasks.check)
    setJibExtension(project.extensions.getByName("jib") as com.google.cloud.tools.jib.gradle.JibExtension)
    doFirst {
        jib?.to?.image = "ghcr.io/$githubRepoOwner/$containerRepoName"
    }
}

// Jib task pushes an image. Only do so after running all checks
tasks.register<com.google.cloud.tools.jib.gradle.BuildImageTask>("jibDockerHub") {
    dependsOn(checkJibDependencies)
    dependsOn(tasks.check)
    setJibExtension(project.extensions.getByName("jib") as com.google.cloud.tools.jib.gradle.JibExtension)
    doFirst {
        jib?.to?.image = "docker.io/recceteam/$containerRepoName"
    }
}

// use different naming when building locally, to avoid confusion
tasks.jibDockerBuild.configure {
    dependsOn(checkJibDependencies)
    jib {
        from {
            platforms {
                platform {
                    architecture = if (System.getProperty("os.arch").equals("aarch64")) "arm64" else "amd64"
                    os = "linux"
                }
            }
        }
        to {
            image = containerRepoName
        }
    }
}

tasks.reckonTagCreate {
    dependsOn(tasks.check)
}
