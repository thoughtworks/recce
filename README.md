# Recce Server

[![Build Status](https://github.com/chadlwilson/recce/actions/workflows/build.yml/badge.svg)](https://github.com/chadlwilson/recce/actions/workflows/build.yml)

Recce is a database reconciliation tool **_for developers_**.

It is intended to make it easier to reconcile arbitrary datasets between **source** and **target** database schemas **on an
ongoing basis** using configured SQL expressions without writing code.

You can read more about the ideas behind Recce at [DESIGN.md](docs/DESIGN.md).

# Development

## Pre-requisites

* Docker
* JDK (if you use [ASDF](https://asdf-vm.com/) you can `asdf install` to install one)

## Getting Started

To get started work on Recce:

* **Build** Lint, Test and compile
    ```shell
    ./gradlew build
    ```
* **Run** Recce within a container with an [example scenario](examples/README.md) against a source/target DB
    ```shell
    ./batect recce
    ```
* **Run** A DB to use with Recce locally
    ```shell
    ./batect db
    ./gradlew run # or run/debug `RecceServer.kt` from your IDE
    ```
* **Build a Docker image** locally and run it
    ```shell
    ./gradlew jibDockerBuild
    ./batect db
    docker run -e DATABASE_HOST=host.docker.internal -p 8080:8080 recce/recce-server
    ```
