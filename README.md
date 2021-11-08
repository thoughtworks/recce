# Recce Server

[![Build Status](https://github.com/chadlwilson/recce/actions/workflows/build.yml/badge.svg)](https://github.com/chadlwilson/recce/actions/workflows/build.yml)

Recce is a database reconciliation tool **_for developers_**.

It is intended to make it easier to reconcile arbitrary datasets between **source** and **target** database schemas **on an
ongoing basis** using configured SQL expressions without writing code.

You can read more about the ideas behind Recce at [DESIGN.md](docs/DESIGN.md).

## Features
* Trigger reconciliation of source and target database schemas using **simple SQL expressions**
* **Privacy** - mo sensitive data stored or output by default. By default, Recce compares **hashes** of data.
* Supports connectivity to **MySQL**, **Postgres** and **MSSQL** databases
* **Database agnostic** when comparing database types - as long as it is coerced to a comparable JVM type consistently, it will be hashed identically

## Getting Started

Currently Recce isn't published externally. To get started, you can build locally. [DEVELOPMENT.md](./DEVELOPMENT.md) has more information, but you can get started with a quick example with

* **Run Recce** locally with an [example](./examples) source and target database
    ```shell
    ./batect recce
    ```
* **Synchronously trigger** a run, waiting for to complete
    ```shell
    curl -X POST http://localhost:8080/runs -H 'Content-Type: application/json' -d '{ "datasetId": "categories" }'
    ``` 
  ```json
  {
    "id": 29,
    "datasetId": "categories",
    "createdTime": "2021-11-08T07:47:59.297424348Z",
    "completedTime": "2021-11-08T07:48:01.149510476Z",
    "sourceMeta": {
      "cols": [
        {
          "name": "MigrationKey",
          "javaType": "String"
        },
        {
          "name": "count(distinct category)",
          "javaType": "Long"
        }
      ]
    },
    "targetMeta": {
      "cols": [
        {
          "name": "MigrationKey",
          "javaType": "String"
        },
        {
          "name": "count(*)",
          "javaType": "Long"
        }
      ]
    },
    "summary": {
      "sourceOnly": 0,
      "targetOnly": 0,
      "bothMatched": 1,
      "bothMismatched": 0,
      "targetTotal": 1,
      "sourceTotal": 1,
      "total": 1
    },
    "completedDurationSeconds": 1.852086128
  }
  ```
* **Retrieve details** of an individual run by ID for a dataset
    ```shell
    curl 'http://localhost:8080/runs/1'
    ```
* **Retrieve details** of recent runs for a dataset
    ```shell
    curl 'http://localhost:8080/runs?datasetId=categories'
    ```

## Configuration

Recce is configured by adding **datasources** and **datasets** that you wish to reconcile.

You can manage configuration as multiple files; or one single file. They will be merged together at runtime.

### Adding additional configuration files for datasets

As a Micronaut application, [configuration can be externalised](https://docs.micronaut.io/latest/guide/#propertySource) in many ways.

However, the recommended way to add additional configuration is to mount a volume with your configuration and set `MICRONAUT_CONFIG_FILES`.

```shell
mkdir -p my-dataset-configs
touch my-dataset-configs/config1.yml my-dataset-configs/config2.yml

# Using a local docker build for demonstration
./gradlew jibDockerBuild

docker run -p 8080:8080 \
  -v $(pwd)/my-dataset-configs:/config \
  -e MICRONAUT_CONFIG_FILES=/config/config1.yml,/config/config2.yml \
  recce/recce-server
```

### Configuring datasources

Arbitrary #s of data sources can be configured in the `r2dbc.datasources` block.

```yaml
r2dbc:
  datasources:
    my-source-db: # Name your datasource anything you want, other than "default"
      # R2DBC URL for your database r2dbc:pool:DB_TYPE://DB_HOST:DB_PORT/DB_NAME
      # DB_TYPE supported = mysql|postgresql|mssql
      url: r2dbc:pool:mysql://source-db:3306/db
      username: user
      password: password
    my-target-db:
      url: r2dbc:pool:mysql://target-db:3306/db
      username: user
      password: password
```

#### Customising datasource connectivity

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

### Configuring datasets

Datasets are groupings of data which
* point to source and target **datasources** configured above
* express queries against source and target that will should produce identical output if the datasets are to be considered **reconciled**
* can use **normal SQL** to express an equivalent data representation between source and target schemas which accounts for intended differences in the way data has been structured and migrated

```yaml
reconciliation:
  datasets:
    pets: # Name your datasets however you would like
      source:
        # Reference to a datasource defined in `r2dbc.datasources`
        dataSourceRef: my-source-db
        # Any SQL query to evaluate against the source DB
        query: >
          SELECT pet.id as MigrationKey, category, name, status
          FROM pet
      target:
      # Reference to a datasource defined in `r2dbc.datasources`  
        dataSourceRef: my-target-db 
          # Any SQL query to evaluate against the source DB
        query: >
          SELECT pet.id as MigrationKey, category.name as category, pet.name, status
          FROM pet INNER JOIN category ON pet.category_id = category.id
```

### Writing queries

The general philosophy of Recce is that differences between source and target are **best handled by the developer** in SQL.

#### The MigrationKey

Recce needs to know which column represents a unique identifier for the row that should be consistent between `source` and `target` and implies these rows represent the **same entity**.

To do this, designate a column by naming it as such
```sql
SELECT natural_id as MigrationKey, some, other, columns
FROM my_table
```

#### Handling differences

You should look to handle differences in types and semantics using the SQL expressions and functions available on the relevant database platform.

For example, if your source database represented an enumeration using integers (`1, 2, 3` etc) whereas your target represented them using `VARCHAR`s, you would use a `CASE` statement to express this expected difference
```sql
-- source
SELECT id as MigrationKey, CASE WHEN enumerated = 1 THEN 'Completed' ELSE 'PENDING' END
FROM my_table
-- target
SELECT id as MigrationKey, enumerated_text
FROM my_table
```

## Development

See [DEVELOPMENT.md](./DEVELOPMENT.md) to get started.
