package com.thoughtworks.recce.server

import io.micronaut.data.jdbc.runtime.JdbcOperations
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.sql.PreparedStatement

@MicronautTest
class DataSourceTest {

    @Inject
    @field:Named("source")
    lateinit var sourceOperations: JdbcOperations

    @Inject
    @field:Named("target")
    lateinit var targetOperations: JdbcOperations


    @Test
    fun `should load multiple data sources`() {
        Assertions.assertThat(countRows(sourceOperations)).isEqualTo(3)
        Assertions.assertThat(countRows(targetOperations)).isEqualTo(4)
    }

    private fun countRows(operations: JdbcOperations): Int? {
        return operations.prepareStatement(
            "select count(*) from testdata"
        ) { preparedStatement: PreparedStatement ->
            val resultSet = preparedStatement.executeQuery()
            if (resultSet.first()) resultSet.getInt(1) else -1
        }
    }
}