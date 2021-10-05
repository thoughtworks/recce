package com.thoughtworks.recce.server

import org.assertj.core.api.Assertions
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Test

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
