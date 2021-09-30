package com.thoughtworks.recce.server

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import org.assertj.core.api.Assertions
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.sql.DataSource

@MicronautTest
class DataSourceTest {

    @Inject
    @field:Named("source")
    lateinit var sourceDataSource: DataSource

    @Inject
    @field:Named("target")
    lateinit var targetDataSource: DataSource

    object TestData : Table() {
        val id = integer("id").autoIncrement()
        val name = varchar("name", 255)
        val value = varchar("value", 255)

        override val primaryKey = PrimaryKey(id)
    }

    private val sourceDb: Database
        get() {
            return Database.connect(sourceDataSource)
        }

    private val targetDb: Database
        get() {
            return Database.connect(targetDataSource)
        }

    @BeforeEach
    fun setup() {
        for (db in listOf(sourceDb, targetDb)) {
            transaction(db) {
                SchemaUtils.create(TestData)

                insertUsers(2)
            }
        }

        transaction(sourceDb) {
            insertUsers(1)
        }
        transaction(targetDb) {
            insertUsers(2)
        }
    }

    private fun insertUsers(num: Int) {
        for (i in 0 until num) {
            TestData.insert {
                it[name] = "Test"
                it[value] = "User"
            }
        }
    }

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
