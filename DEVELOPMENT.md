<!-- ToC auto-populated via https://github.com/ekalinin/github-markdown-toc -->
<!--ts-->
* [Pre-requisites](DEVELOPMENT.md#pre-requisites)
* [Getting Started](DEVELOPMENT.md#getting-started)
* [IDE Setup](DEVELOPMENT.md#ide-setup)
   * [COnfiur](DEVELOPMENT.md#confiur)
* [Setting up new folks for access](DEVELOPMENT.md#setting-up-new-folks-for-access)
   * [Add user to GitHub org](DEVELOPMENT.md#add-user-to-github-org)
   * [Pulling container images](DEVELOPMENT.md#pulling-container-images)
* [Technical Overview](DEVELOPMENT.md#technical-overview)
   * [Recce Database](DEVELOPMENT.md#recce-database)
   * [External data sources for Reconciliation](DEVELOPMENT.md#external-data-sources-for-reconciliation)
   * [Build](DEVELOPMENT.md#build)
   * [Testing](DEVELOPMENT.md#testing)
      * [Testing conventions](DEVELOPMENT.md#testing-conventions)
      * [Micronaut Tests](DEVELOPMENT.md#micronaut-tests)

<!-- Added by: runner, at: Mon Dec 13 03:27:47 UTC 2021 -->

<!--te-->

# Pre-requisites

* Docker
* JDK 17 (if you use [ASDF](https://asdf-vm.com/) you can `asdf install` to install one using the [.tool-versions](./.tool-versions))

# Getting Started

To get started working on Recce:

* **Build** Lint, Test and compile
    ```shell
    ./gradlew build
    ./batect build # Alternative within container. Will be slower, but perhaps cleaner.
    ```
* **Run** Recce within a container with an [example scenario](examples/README.md) against a source/target DB
    ```shell
    ./batect run
    ```
* **Explore** Recce's API (after running it) via http://localhost:8080/rapidoc
* **Build a Docker image** locally and run it with an [example scenario](examples/README.md) a
    ```shell
    ./gradlew jibDockerBuild && ./batect run-docker-local
    ```
* **Run** A DB on its own to use with Recce locally
    ```shell
    ./batect run-db
    ./gradlew run # or run/debug `RecceServer.kt` from your IDE
    ```
* **Run** Only Recce's dependencies with an [example scenario](examples/README.md), so you can run/debug/profile Recce itself outside Docker
    ```shell
    ./batect run-deps
    ./gradlew run # or run/debug `RecceServer.kt` from your IDE
    ```

# IDE Setup

If using IntelliJ IDEA, you should be able to create the project from the clone location and IntelliJ will detect it as a Gradle project and import it directly. There are a couple of minor tweaks to make.

* *Project Settings* > *Project* > Ensure SDK and language level is set to Java 17
* *Gradle* > *Gradle Settings* > Set `Run Tests Using` to `IntelliJ IDEA`.
  * This will typically give you much faster feedback than waiting for the Gradle runner. Unfortunately at time of writing you cannot build using IntelliJ IDEA natively, because kapt annotation processing for Kotlin is [not supported outside of Gradle/Maven builds](https://youtrack.jetbrains.com/issue/KT-15040).

## COnfiur

# Setting up new folks for access

While this remains an internal project there are some things to do

## Add user to GitHub org
* Ask a `Manager` within [the NEO team](https://neo.thoughtworks.net/teams/8FiOTVa06k/Regional_IT_-_SEA_-_China_Regional_IT) to invite the user via their Thoughtworks identity
* New person accepts the invite and does an Okta "dance" to accept the invitation.
* Everyone in the org has read access. If the new user is expected to commit, ask them to be added to [the GitHub (sub-)team](https://github.com/orgs/ThoughtWorks-SEA/teams/recce)
* To clone with SSH, user will need to [authorize their SSH key for use with SSO](https://docs.github.com/en/authentication/authenticating-with-saml-single-sign-on/authorizing-an-ssh-key-for-use-with-saml-single-sign-on) for the org.

## Pulling container images
Pulling officially built Docker images locally requires some additional setup to authenticate with the GitHub Container Registry: 
* Generate a personal access token in [your account](https://github.com/settings/tokens) with `packages:read` permission.
* Use Configure SSO to [authorize the token for SSO access via the organisation](https://docs.github.com/en/authentication/authenticating-with-saml-single-sign-on/authorizing-a-personal-access-token-for-use-with-saml-single-sign-on)
* Login with something like the below (see [here](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry) for details)
    ```shell
   echo "ghp_REST_OF_TOKEN" | docker login https://ghcr.io -u my-github-username --password-stdin
    ```
* Then `docker pull ghcr.io/thoughtworks-sea/recce-server` etc should work.
 
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
- Recce uses raw Micronaut Data R2DBC SQL to execute configured queries ([example](examples/scenario/petshop-mysql/application-petshop-mysql.yml)) defined against external databases

## Build
- Gradle (Kotlin-style) is used build automation
- Code is linted using [Spotless Gradle](https://github.com/diffplug/spotless/tree/main/plugin-gradle), with [ktlint](https://github.com/pinterest/ktlint) for Kotlin.
    - _Tip_: Spotless/ktlint can auto-fix a lot of nitpicks with `./gradlew spotlessApply`
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
Due to the config-driven nature of the tool, there are a number of tests which load Micronaut configuration via `@MicronautTest` or `ApplicationContext.run(props)`. Since certain config files are automatically loaded, to keep these as fast as possible the default configurations in [`application.yml`](./src/main/resources/application.yml) and [`application-test.yml`](https://github.com/ThoughtWorks-SEA/recce/blob/master/src/test/resources/application-test.yml) should be as light as possible and avoid doing slow things, triggering automated processes etc.
