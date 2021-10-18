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
* **Run** Recce within a container with example source/target DBs
    ```shell
    ./batect recce
    ```
* **Build a Docker image** locally and run it
    ```shell
    ./gradlew jibDockerBuild
    docker run recce/recce-server
    ```
