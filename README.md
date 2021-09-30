# Recce Server

Recce is a database reconciliation tool **_for developers_**.

It is intended to make it easier to reconcile arbitrary datasets between **source** and **target** databases **on an
ongoing basis** using simple SQL expressions.

You can read more about the ideas behind Recce at [DESIGN.md](docs/DESIGN.md).

# Development

## Pre-requisites

* Docker
* JDK (if you use [ASDF](https://asdf-vm.com/)) you can `asdf install` to install one

## Getting Started

You can get started work on Recce by compiling

* **Build** Lint, Test and compile
    ```shell
    ./gradlew build
    ```
* **Run** Recce within Gradle
    ```shell
    ./gradlew run
    ```
* Build a Docker image locally and run it
    ```shell
    ./gradlew jobDockerBuild
    docker run recce/recce-server
    ```