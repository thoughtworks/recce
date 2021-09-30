package com.thoughtworks.recce.server

import io.micronaut.runtime.Micronaut.build

fun main(args: Array<String>) {
    build()
        .args(*args)
        .packages("com.thoughtworks.recce.server")
        .start()
}
