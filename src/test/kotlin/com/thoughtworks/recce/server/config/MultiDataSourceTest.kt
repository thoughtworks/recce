package com.thoughtworks.recce.server.config

import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Testcontainers
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@Testcontainers
// @MicronautTest(environments = arrayOf("test-multisource"))
internal class MultiDataSourceTest : DataSourceTest() {

    private var source: ConnectionFactory = ConnectionFactories.get(
        ConnectionFactoryOptions.parse(mysql.jdbcUrl.replace("jdbc", "r2dbc"))
            .mutate()
            .option(ConnectionFactoryOptions.USER, mysql.username)
            .option(ConnectionFactoryOptions.PASSWORD, mysql.password)
            .build()
    )

    private var target: ConnectionFactory = ConnectionFactories.get(
        ConnectionFactoryOptions.parse(postgre.jdbcUrl.replace("jdbc", "r2dbc"))
            .mutate()
            .option(ConnectionFactoryOptions.USER, postgre.username)
            .option(ConnectionFactoryOptions.PASSWORD, postgre.password)
            .build()
    )

    @Test
    fun `should load data from reactive datasource`() {
        StepVerifier.create(getCount(source))
            .expectNext(3)
            .verifyComplete()

        StepVerifier.create(getCount(target))
            .expectNext(4)
            .verifyComplete()
    }

    private fun getCount(connectionFactory: ConnectionFactory) =
        Mono.from(connectionFactory.create())
            .flatMapMany { it.createStatement("SELECT count(*) as count from TestData").execute() }
            .flatMap { result -> result.map { row, _ -> row.get("count") as Long } }
}
