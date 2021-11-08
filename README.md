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
* **Database agnostic** when comparing data types - 

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

## Development

See [DEVELOPMENT.md](./DEVELOPMENT.md) to get started.
