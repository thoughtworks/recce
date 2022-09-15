# Recce Server

[![Build Status](https://github.com/thoughtworks-sea/recce/actions/workflows/build.yml/badge.svg)](https://github.com/thoughtworks-sea/recce/actions/workflows/build.yml)
[![Coverage](https://img.shields.io/codecov/c/github/thoughtworks-sea/recce.svg)](https://codecov.io/gh/thoughtworks-sea/recce)
[![License](https://img.shields.io/github/license/thoughtworks-sea/recce.svg)](https://opensource.org/licenses/Apache-2.0)

Recce (_rek-ee_) is a database reconciliation tool **_for developers_**.

It is intended to make it easier to reconcile arbitrary datasets between **source** and **target** database schemas **on an
ongoing basis** using configured SQL expressions without writing code.

You can read more about the ideas behind Recce at [DESIGN.md](docs/DESIGN.md) or in the [FAQ](#faq).

## Features
* 🔥 Trigger reconciliation of source and target database schemas using **simple SQL expressions**
* 🔒 **Privacy** - no sensitive data stored or output by default. By default, Recce compares **hashes** of data.
* ✏️ **Flexible configuration** to map groups of data to reconcile to different datasources and databases 
* 🤝 Supports connectivity to **MySQL**, **Postgres**, **MSSQL**, **MariaDB**, **AWS Aurora RDS MySQL**, **Oracle** databases
* 💪🏾 **Database agnostic** when comparing database types - as long as it is coerced to a comparable JVM type consistently, it will be hashed identically
* ⏰ **Schedule** reconciliations for low-traffic periods against your data sources

## Table of contents
<!-- ToC auto-populated via https://github.com/ekalinin/github-markdown-toc -->
<!--ts-->
* [Recce Server](README.md#recce-server)
   * [Features](README.md#features)
   * [Table of contents](README.md#table-of-contents)
* [Getting Started](README.md#getting-started)
* [Configuration](README.md#configuration)
   * [Configuring Recce itself](README.md#configuring-recce-itself)
   * [Database configuration conveniences](README.md#database-configuration-conveniences)
   * [Adding additional configuration files](README.md#adding-additional-configuration-files)
   * [Configuring authentication](README.md#configuring-authentication)
   * [Configuring datasources](README.md#configuring-datasources)
      * [Driver/database specific configuration](README.md#driverdatabase-specific-configuration)
      * [Customising datasource pooling](README.md#customising-datasource-pooling)
   * [Configuring datasets](README.md#configuring-datasets)
   * [Writing dataset queries](README.md#writing-dataset-queries)
      * [The MigrationKey](README.md#the-migrationkey)
      * [Ordering of columns](README.md#ordering-of-columns)
      * [Handling differences](README.md#handling-differences)
      * [Huge datasets and aggregates](README.md#huge-datasets-and-aggregates)
* [Development](README.md#development)
* [FAQ](README.md#faq)
<!--te-->

# Getting Started

Recce is currently [published and available as a container image via GHCR](https://github.com/ThoughtWorks-SEA/recce/pkgs/container/recce-server).

However, it also
* requires a Postgres database
* ...and to be practically useful you will want to configure it with connectivity to some data sources you wish to reconcile.

These options require only JDK 11+ and Docker installed locally.

1. **Run Recce** locally
    * Either **Build** locally with an [example](./examples) source and target database (More info at [DEVELOPMENT.md](./DEVELOPMENT.md)).
    ```shell
    ./batect run
    ```
    * **Or use pre-validated Docker image** locally, using this repository only for setting up a DB for Recce, and an example scenario.
    ```shell
    # Run in one shell - starts a DB for Recce, and an example scenario
    ./batect run-deps
  
    # Run in another shell - runs Recce
    docker run -p 8080:8080 \
      -v $(pwd)/examples/scenario/petshop-mariadb:/examples/scenario/petshop-mariadb \
      -e AUTH_USERNAME=admin -e AUTH_PASSWORD=admin \
      -e MICRONAUT_CONFIG_FILES=/examples/scenario/petshop-mariadb/application-petshop-mariadb.yml \
      -e DATABASE_HOST=host.docker.internal \
      -e R2DBC_DATASOURCES_SOURCE_URL=r2dbc:pool:mysql://host.docker.internal:8000/db \
      -e R2DBC_DATASOURCES_TARGET_URL=r2dbc:pool:mysql://host.docker.internal:8001/db \
      ghcr.io/thoughtworks-sea/recce-server:latest
    ```
    
2. **Explore** and trigger runs via Recce's APIs, accessible via interactive UI at http://localhost:8080/rapidoc. 
    Some non-exhaustive examples are included below, but fuller documentation is available via the UI. 
    Recce uses basic authentication to protect its API endpoints. In the example scenario, both username and password are set to "admin".
 
    * **Synchronously trigger** a run, waiting for it to complete via [UI](http://localhost:8080/rapidoc#post-/runs) _or_
      ```shell
      curl -X POST http://localhost:8080/runs -H 'Content-Type: application/json' -d '{ "datasetId": "categories" }' -u "admin:admin"
      ``` 
      <details>
        <summary>Expand example results</summary>

        ```json
        {
          "completedDurationSeconds": 0.736356642,
          "completedTime": "2021-12-07T10:37:35.469576795Z",
          "createdTime": "2021-12-07T10:37:34.733220153Z",
          "datasetId": "categories",
          "id": 35,
          "summary": {
            "bothMatchedCount": 1,
            "bothMismatchedCount": 0,
            "source": {
              "meta": {
                "cols": [
                  {
                    "javaType": "String",
                    "name": "MigrationKey"
                  },
                  {
                    "javaType": "Long",
                    "name": "count(distinct category)"
                  }
                ]
              },
              "onlyHereCount": 0,
              "totalCount": 1
            },
            "target": {
              "meta": {
                "cols": [
                  {
                    "javaType": "String",
                    "name": "MigrationKey"
                  },
                  {
                    "javaType": "Long",
                    "name": "count(*)"
                  }
                ]
              },
              "onlyHereCount": 0,
              "totalCount": 1
            },
            "totalCount": 1
          }
        }
        ```
     </details>
   
    * **Retrieve details of an individual run** by ID for a dataset via [UI](http://localhost:8080/rapidoc#get-/runs/-runId-), _or_
      ```shell
      curl 'http://localhost:8080/runs/35' -u "admin:admin"
      ```
    * **Retrieve details of recent runs** for a dataset via [UI](http://localhost:8080/rapidoc#get-/runs), _or_
      ```shell
      curl 'http://localhost:8080/runs?datasetId=categories' -u "admin:admin"
      ```

# Configuration

Recce is configured by adding **datasources** and **datasets** that you wish to reconcile.

You can manage configuration as multiple files; or one single file. They will be merged together at runtime.

## Configuring Recce itself

As a Micronaut application, much of Recce's configuration is open for hacking and can be [expressed in multiple ways](https://docs.micronaut.io/latest/guide/#propertySource).

Recce-specific configuration (as opposed to all possible generic Micronaut configuration) is documented within the [default config](src/main/resources/application.yml).

## Database configuration conveniences

To make it easier to configure Recce's own DB, some dedicated properties are respected to construct the appropriate URL
including connection pooling.

| Env value         | Default     | Description                                      |
|-------------------|-------------|--------------------------------------------------|
| DATABASE_HOST     | `localhost` | Host your Postgres DB is on                      |
| DATABASE_PORT     | `9000`      | Port your Postgres DB is on                      |
| DATABASE_NAME     | `db`        | The name of the logical database within Postgres |
| DATABASE_USERNAME | `user`      | Username to connect with                         |
| DATABASE_PASSWORD | `password`  | Password to connect with                         |

In the normal Micronaut way, additional configuring can be configured using
* `R2DBC_DATASOURCES_DEFAULT_*` environment variables, _or_
* `r2dbc.datasource.default.*` system properties _or_
* Merging in an additional YAML configuration file (see below)

## Adding additional configuration files

As a Micronaut application, [configuration can be externalised](https://docs.micronaut.io/latest/guide/#propertySource) in many ways.

However, the recommended way to add additional configuration **for your own datasources and datasets** to reconcile is to 
mount a volume with your configuration and set `MICRONAUT_CONFIG_FILES` to point Recce at your additional configuration 
which will be merged into the base configuration. This allows you to separate and manage configuration as appropriate
for your environment.

```shell
mkdir -p my-dataset-configs
touch my-dataset-configs/config1.yml my-dataset-configs/config2.yml

## Run latest from docker
docker run -p 8080:8080 \
  -v $(pwd)/my-dataset-configs:/config \
  -e MICRONAUT_CONFIG_FILES=/config/config1.yml,/config/config2.yml \
  ghcr.io/thoughtworks-sea/recce-server:latest
```

## Configuring authentication

Recce supports only basic authentication for API endpoints out of the box. The credentials used in basic authentication
to protect the API endpoints can be configured in the `auth` block of your config file.

```yaml
auth:
    username: some-username
    password: some-password
```

or read from the environment:
```bash
export AUTH_USERNAME=some-username
export AUTH_PASSWORD=some-password
```

## Configuring datasources

Arbitrary #s of data sources can be configured in the `r2dbc.datasources` block of your config file.

```yaml
r2dbc:
  datasources:
    my-source-db: # Name your datasource anything you want, other than "default"
      # R2DBC URL for your database r2dbc:pool:DB_TYPE://DB_HOST:DB_PORT/DB_NAME
      # DB_TYPE supported = mysql|postgresql|mssql|mariadb|oracle
      url: r2dbc:pool:mysql://source-db:3306/db
      username: user
      password: password
    my-target-db:
      url: r2dbc:pool:mysql://target-db:3306/db
      username: user
      password: password
```

### Driver/database specific configuration

For configuration specific to a given driver/DB you can consult their documentation. Usually additional settings needs to be in the `options:` block; or sometimes inside the connection URL.
* [MySQL](https://github.com/mirromutth/r2dbc-mysql#configuration-items)
* [Postgres](https://github.com/pgjdbc/r2dbc-postgresql#getting-started)
* [MS SQL Server](https://github.com/r2dbc/r2dbc-mssql#getting-started)
* [MariaDB](https://github.com/mariadb-corporation/mariadb-connector-r2dbc#connection-options) (can also be used for MySQL, as well as AWS RDS Aurora MySQL)
* [Oracle](https://github.com/oracle/oracle-r2dbc#connection-creation)

### Customising datasource pooling

By default, Recce is deployed with [r2dbc-pool](https://github.com/r2dbc/r2dbc-pool) to manage connection pooling to data sources. If you remove `:pool` from the URL, this will be disabled.

You can thus customise the connection pool size, etc
```yaml
r2dbc:
  datasources:
    my-source-db:
      url: r2dbc:pool:mysql://source-db:3306/db
      username: user
      password: password
      options: # Connection Pool options
        initialSize: 1
        maxSize: 5
        maxLifeTime: 5
        # etc, see https://github.com/r2dbc/r2dbc-pool
```

## Configuring datasets

Datasets are the heart of your configuration. This tells Recce **what** and **how** to reconcile logically equivalent chunks of data.

Datasets are groupings of data which
* point to source and target **datasources** configured above
* express queries against source and target that will should produce identical output if the datasets are to be considered **reconciled**
* can use **normal SQL** to express an equivalent data representation between source and target schemas which accounts for intended differences in the way data has been structured and migrated
* can be scheduled to run on regular intervals

```yaml
reconciliation:
  datasets:
    my-data-set: # Name your datasets however you would like
      source:
        # Reference to a datasource defined in `r2dbc.datasources`
        datasourceRef: my-source-db
        # Specify whether to load inline query or read from a query file
        # If both `query` and `queryFile` are provided, inline query takes precedence
        # If both query and queryFile are not specified,
        # load the query statement from ${dataset-name}-${source-or-target}.sql in `queryFileBaseDir`
        # Optional SQL query to evaluate against the source DB
        query: >
          SELECT id AS MigrationKey, * FROM my-table
        # Optional path to file containing query to evaluate against the source DB
        queryFile: examples/scenario/<{example-scenario}/queries/<{example-scenario}-my-data-set-source.yml
      target:
        # Reference to a datasource defined in `r2dbc.datasources`
        datasourceRef: my-target-db
        # Specify whether to load inline query or read from a query file
        # If both `query` and `queryFile` are provided, inline query takes precedence
        # If both query and queryFile are not specified,
        # load the query statement from ${dataset-name}-${source-or-target}.sql in `queryFileBaseDir`
        # Optional SQL query to evaluate against the target DB
        query: >
          SELECT id AS MigrationKey, * FROM my-table
        # Optional path to file containing query to evaluate against the target DB
        queryFile: examples/scenario/<{example-scenario}/queries/<{example-scenario}-my-data-set-target.yml
      # Optional strategy for determining equivalence of column values
      hashingStrategy: TypeLenient
      # Optional scheduling of regular or one-of reconciliations
      schedule:
        # Must adhere to format https://docs.micronaut.io/latest/api/io/micronaut/scheduling/cron/CronExpression.html
        # or https://crontab.guru/ (without seconds)
        cronExpression: 0 0 * * *
```
Fuller example Recce-specific configuration is [available here](src/main/resources/application.yml).

## Writing dataset queries

The general philosophy of Recce is that differences between source and target are **best handled by the developer** using regular SQL queries.

### The MigrationKey

Recce needs to know which column represents a unique identifier for the row that should be consistent between `source` and `target` and implies these rows represent the **same entity**.

To do this, designate a column by naming it as `MigrationKey` (case insensitive)
```sql
SELECT natural_id AS MigrationKey, some, other, columns
FROM my_table
```

Recce will complain if there is more than one column in your dataset with this name.

### Ordering of columns

Currently Recce ignores names of columns _other than_ the `MigrationKey` column. That means that the **order of columns is critical and must match** between your two queries.

If the column in position 3 represents datum `X` in the `source` dataset, then the column in position 3 in the `target` dataset should also represent the same datum.

The data types of the columns need not match exactly; however if the values produce different hashes, this will lead to a row mismatch. For example an integer of `111` in source will produce a different hash to a string of `"111"` in target. If you want these values to be considered identical, you should use SQL to coerce the data types to match and express this intended difference.

If you would like to see a `nameBased` column matching option, consider adding your thoughts to #55.

### Handling differences

You should look to handle differences in types and semantics using the SQL expressions and functions available on the relevant database platform.

For example, if your source database represented an enumeration using integers (`1, 2, 3` etc) whereas your target represented them using `VARCHAR`s, you would use a `CASE` statement to express this expected difference
```sql
-- source
SELECT id AS MigrationKey, CASE WHEN enumerated = 1 THEN 'Completed' ELSE 'PENDING' END
FROM my_table
-- target
SELECT id AS MigrationKey, enumerated_text
FROM my_table
```

### Huge datasets and aggregates

When reconciling very large data sets, it may not be feasible to do row-by-row, column-by-column comparisons. In such cases it may be sufficient to use aggregate queries to get an idea whether you have lost data. e.g

```sql
--- check #s of login audit records for a month of data by location and type
SELECT user_location || user_type AS MigrationKey, count(*) AS Count
FROM user_login_audit
WHERE user_login_datetime >= timestamp('2021-11-01 00:00:00') AND
    user_login_datetime < timestamp('2021-12-01 00:00:00')
GROUP BY user_location, user_type
```

# Development

See [DEVELOPMENT.md](./DEVELOPMENT.md) to get started.

# FAQ

See [FAQ.md](./FAQ.md).
