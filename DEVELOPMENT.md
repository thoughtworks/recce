<!-- ToC auto-populated via https://github.com/ekalinin/github-markdown-toc -->
<!--ts-->
* [Pre-requisites](DEVELOPMENT.md#pre-requisites)
* [Getting Started](DEVELOPMENT.md#getting-started)
* [IDE Setup](DEVELOPMENT.md#ide-setup)
* [Technical Overview](DEVELOPMENT.md#technical-overview)
   * [Recce Database](DEVELOPMENT.md#recce-database)
   * [External data sources for Reconciliation](DEVELOPMENT.md#external-data-sources-for-reconciliation)
   * [Build](DEVELOPMENT.md#build)
   * [Testing](DEVELOPMENT.md#testing)
      * [Testing conventions](DEVELOPMENT.md#testing-conventions)
      * [Micronaut Tests](DEVELOPMENT.md#micronaut-tests)
* [Common problems](DEVELOPMENT.md#common-problems)
   * [Running tests via Colima](DEVELOPMENT.md#running-tests-via-colima)
   * [Out of Memory errors running within containers](DEVELOPMENT.md#out-of-memory-errors-running-within-containers)
* [For maintainers](DEVELOPMENT.md#for-maintainers)
   * [Releasing](DEVELOPMENT.md#releasing)
<!--te-->

# Pre-requisites

* Docker CLI
* Docker Desktop, [Colima](https://github.com/abiosoft/colima) or some mechanism to run containers on your host
* JDK 17 (if you use [RTX](https://github.com/jdxcode/rtx) or [ASDF](https://asdf-vm.com/) you can `rtx install` or `asdf install` to install one using the [.tool-versions](./.tool-versions))

# Getting Started

To get started working on Recce:

* **Build** Lint, Test and compile
    ```shell
    ./gradlew build
    ./batect build # Alternative within container. Will be slower, but perhaps cleaner.
    ```
* **Run** Recce within a container with an [example scenario](examples/README.md) against a source/target DB
    ```shell
    ./batect -f examples/batect-petshop-mariadb.yml run
    ```
* **Explore** Recce's API (after running it) via http://localhost:8080/rapidoc/
* **Build a Docker image** locally and run it with an [example scenario](examples/README.md) a
    ```shell
    ./gradlew jibDockerBuild && ./batect -f examples/batect-petshop-mariadb.yml run-docker-local
    ```
* **Run** A PostgreSQL DB on its own to use with Recce locally
    ```shell
    ./batect run-db
    ./gradlew run # or run/debug `RecceServer.kt` from your IDE
    ```
* **Run** Only Recce's dependencies with an [example scenario](examples/README.md), so you can run/debug/profile Recce itself outside Docker
    ```shell
    ./batect -f examples/batect-petshop-mariadb.yml run-deps
    ./gradlew run # or run/debug `RecceServer.kt` from your IDE
    ```

# IDE Setup

If using IntelliJ IDEA, you should be able to create the project from the clone location and IntelliJ will detect it as a Gradle project and import it directly. There are a couple of minor tweaks to make.

* *Project Settings* > *Project* > Ensure SDK and language level is set to Java 17
* *Gradle* > *Gradle Settings* > Set `Run Tests Using` to `IntelliJ IDEA`.
  * This will typically give you much faster feedback than waiting for the Gradle runner. Unfortunately at time of writing you cannot build using IntelliJ IDEA natively, because kapt annotation processing for Kotlin is [not supported outside of Gradle/Maven builds](https://youtrack.jetbrains.com/issue/KT-15040).
 
# Technical Overview

Recce is a [Micronaut](https://docs.micronaut.io/latest/guide/) JVM application written in [Kotlin](https://kotlinlang.org/) using a broadly reactive asynchronous style using [Project Reactor](https://projectreactor.io/).
- Micronaut is used to externalise configuration for both Recce itself and configuration of new data sources
- Micronaut [User Guide](https://docs.micronaut.io/latest/guide/index.html) | [API Reference](https://docs.micronaut.io/latest/api/index.html) | [Configuration Reference](https://docs.micronaut.io/latest/guide/configurationreference.html) | [Guides](https://guides.micronaut.io/index.html)

## Recce Database
- Recce has its own Postgresql database which is used to 
  - store details of reconciliation runs
  - store results for *datasets*, which may include row-by-row hashes
- Connectivity to Recce's own database uses [R2DBC](https://r2dbc.io/) rather than JDBC to allow end-to-end non-blocking access
- As a relatively simply DB application, it uses [Micronaut Data R2DBC](https://micronaut-projects.github.io/micronaut-data/latest/guide/#r2dbcQuickStart) as an ORM library (rather than a full JPA implementation such as Hibernate). Micronaut Data is not as fully featured as something such as Hibernate.
- DB migrations are being handled with [Flyway](https://flywaydb.org/)

## External data sources for Reconciliation
- Recce uses raw Micronaut Data R2DBC SQL to execute configured queries ([example](examples/scenario/petshop-mariadb/application-petshop-mariadb.yml)) defined against external databases

## Build
- Gradle (Kotlin-style) is used for build automation
- Code is linted using [Spotless Gradle](https://github.com/diffplug/spotless/tree/main/plugin-gradle), with [ktlint](https://github.com/pinterest/ktlint) for Kotlin.
    - _Tip_: Spotless/ktlint can auto-fix a lot of nitpicks with `./gradlew spotlessApply`
- [Detekt](https://detekt.dev/) & [FindSecBugs](https://find-sec-bugs.github.io/) (with [SpotBugs](https://spotbugs.github.io/)) are used for static analysis from a coding practices and security perspective.
- [Batect](https://batect.dev/) is available to automate dev+testing tasks within containers, including running Recce locally
- [GitHub Actions](.github/workflows) are being used to automate build+test

## Testing

- Tests are written using **JUnit Jupiter** with **AssertJ** and **Mockito** for mocking support
- At time of writing, Recce's own DB tests are tested against **H2 Database** rather than Postgres, for improved feedback, however this imposes some limitations and may need to be re-evaluated later
- [Testcontainers](https://www.testcontainers.org/) are used for starting external DBs of various types to test against
- [Rest-assured](https://rest-assured.io/) is used for API tests

### Testing conventions
These tend to evolve over time and should be re-evaluated as needed, however

* Tests named `*IntegrationTest` involve integration with a normally separate dependent component, such as a database.
* Tests named `*ApiTest` test an API via Micronaut HTTP using real HTTP calls, thus verifying the contract
* Other tests are mainly pure unit tests; although might do "fast" things such as use Micronaut to load configuration

### Micronaut Tests
Due to the config-driven nature of the tool, there are a number of tests which load Micronaut configuration via `@MicronautTest` or `ApplicationContext.run(props)`. Since certain config files are automatically loaded, to keep these as fast as possible the default configurations in [`application.yml`](./src/main/resources/application.yml) and [`application-test.yml`](src/test/resources/application-test.yml) should be as light as possible and avoid doing slow things, triggering automated processes etc.

# Common problems

## Running tests via Colima

Some tests run within containers via Testcontainers. You might have issues if you are using [Colima](https://github.com/abiosoft/colima) rather than Docker.

Try setting the below per [this issue](https://github.com/testcontainers/testcontainers-java/issues/5034#issuecomment-1036433226).
```shell
export DOCKER_HOST="unix:///${HOME}/.colima/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

## Out of Memory errors running within containers

Running `./batect` run fails and returns an error with error code `137`. The stack trace also indicates a `HeapDumpOnOutOfMemoryError`.

If you're running on MacOS using Colima or Docker Desktop, the default VM created might be too small. When running the example scenarios, it starts multiple containers including the 'example' MariaDB databases. Hence, the VM needs more memory (at least 3GB) to avoid having to constrain Recce’s memory usage.

* With Docker Desktop you can adjust using the GUI.
* When using Colima, this can be done by recreating your colima VM with something like the below. 
    ```shell
    colima delete && colima start --cpu 6 --memory 6
    ```

# For maintainers

Currently, core maintainer privileges are only available to Thoughtworkers. A setup guide is available [here (internal only)](https://docs.google.com/document/d/1r56rDyGOnRQAAMyHtUHflML1szdvQJMi8p3bzMNB_8A/edit#).

## Releasing

Needs to be more automated. Current we are experimenting with using [Reckon](https://github.com/ajoberstar/reckon) to determine versions from flags.

Current release process looks like
1. Tag locally and push. This will run a local build; if successful tag the revision and push the tag.
    ```shell
    # Minor version (e.g 1.1 -> 1.2)
    ./gradlew -Preckon.stage=final reckonTagPush
   
    # Patch version (e.g 0.6.0 -> 0.6.1)   
    ./gradlew -Preckon.stage=final -Preckon.scope=patch reckonTagPush
    ```
2. The tag push will trigger a build on [GitHub Actions](https://github.com/ThoughtWorks-SEA/recce/actions) and push to GHCR.
3. Create a new release on Github via https://github.com/ThoughtWorks-SEA/recce/releases linked to the tag
