package com.thoughtworks.recce.server.config

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.assertj.core.api.Assertions
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Test

@MicronautTest(environments = arrayOf("test-integration"))
class JdbcDataSourceTest : DataSourceTest() {

    @Test
    fun `should load multiple data sources`() {
        transaction(sourceDb) {
            Assertions.assertThat(TestData.selectAll().count()).isEqualTo(3)
        }
        transaction(targetDb) {
            Assertions.assertThat(TestData.selectAll().count()).isEqualTo(4)
        }
    }
}
