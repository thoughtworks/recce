## Pre-requisites

* Docker
* JDK 16 (if you use [ASDF](https://asdf-vm.com/) you can `asdf install` to install one using the [.tool-versions](./.tool-versions))

## Getting Started

To get started work on Recce:

* **Build** Lint, Test and compile
    ```shell
    ./gradlew build
    ./batect build # Alternative within container. Will be slower, but perhaps cleaner.
    ```
* **Run** Recce within a container with an [example scenario](examples/README.md) against a source/target DB
    ```shell
    ./batect run
    ```
* **Build a Docker image** locally and run it with an [example scenario](examples/README.md) a
    ```shell
    ./gradlew jibDockerBuild && ./batect run-docker-prebuilt
    ```
* **Run** A DB on its own to use with Recce locally
    ```shell
    ./batect db
    ./gradlew run # or run/debug `RecceServer.kt` from your IDE
    ```
 
## Technical Overview

Recce is a [Micronaut](https://docs.micronaut.io/latest/guide/) JVM application written in [Kotlin](https://kotlinlang.org/) using a broadly reactive asynchronous style using [Project Reactor](https://projectreactor.io/).
- Micronaut is used to externalise configuration for both Recce itself and configuration of new data sources
- Micronaut [User Guide](https://docs.micronaut.io/latest/guide/index.html) | [API Reference](https://docs.micronaut.io/latest/api/index.html) | [Configuration Reference](https://docs.micronaut.io/latest/guide/configurationreference.html) | [Guides](https://guides.micronaut.io/index.html)

### Recce Database
- Recce has its own Postgresql database which is used to 
  - store details of reconciliation runs
  - store results for *datasets*, which may include row-by-row hashes
- Connectivity to Recce's own database uses [R2DBC](https://r2dbc.io/) rather than JDBC to allow end-to-end non-blocking access
- As a relatively simply DB application, it uses [Micronaut Data R2DBC](https://micronaut-projects.github.io/micronaut-data/latest/guide/#r2dbcQuickStart) as an ORM library (rather than a full JPA implementation such as Hibernate). Micronaut Data is not as fully featured as something such as Hibernate.
- DB migrations are being handled with [Flyway](https://flywaydb.org/)

### External data sources for Reconciliation
- Recce uses raw Micronaut Data R2DBC SQL to execute configured queries ([example](examples/scenario/simple-mysql/application-simple-mysql.yml)) defined against external databases

### Testing

- Tests are written using **JUnit Jupiter** with **AssertJ** and **Mockito** for mocking support
- At time of writing, Recce's own DB tests are tested against **H2 Database** rather than Postgres, for improved feedback, however this imposes some limitations and may need to be re-evaluated later
- [Testcontainers](https://www.testcontainers.org/) are used for starting external DBs of various types to test against
- [Rest-assured](https://rest-assured.io/) is used for API tests
- Test data is set up in databases using the [Exposed framework](https://github.com/JetBrains/Exposed). Unfortunately this only allows JDBC style access, and thus requires regular JDBC drivers to be available for tests, in addition to the R2DBC drivers used at runtime

### Build
- Gradle (Kotlin-style) is used to build
- [Batect](https://batect.dev/) is available to automate dev+testing tasks within containers, including running Recce locally
- [GitHub Actions](.github/workflows) are being used to automate build+test
