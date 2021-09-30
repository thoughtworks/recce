package com.thoughtworks.recce.server

import io.micronaut.runtime.Micronaut.*

fun main(args: Array<String>) {
    build()
        .args(*args)
        .packages("com.thoughtworks.recce.server")
        .start()
}
