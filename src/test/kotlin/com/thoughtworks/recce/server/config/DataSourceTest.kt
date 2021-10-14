package com.thoughtworks.recce.server.config

import jakarta.inject.Inject
import jakarta.inject.Named
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import javax.sql.DataSource

abstract class DataSourceTest {

    companion object {
        @JvmStatic
        @Container
        protected val mysql: MySQLContainer<Nothing> = MySQLContainer<Nothing>("mysql:8").apply {
            withDatabaseName("sourceDb")
            withUsername("sa")
            withPassword("pasword")
        }

        @JvmStatic
        @Container
        protected val postgre: PostgreSQLContainer <Nothing> = PostgreSQLContainer<Nothing>("postgres:13-alpine").apply {
            withDatabaseName("targetdb")
            withUsername("sa")
            withPassword("pasword")
        }
    }

    @Inject
    @field:Named("target")
    protected lateinit var targetDataSource: DataSource

    protected object TestData : Table() {
        val id = integer("id").autoIncrement()
        val name = varchar("name", 255)
        val value = varchar("value", 255)

        override val primaryKey = PrimaryKey(id)
    }

    protected val sourceDb: Database
        get() {
            return Database.connect(mysql.jdbcUrl, user = mysql.username, password = mysql.password)
        }

    protected val targetDb: Database
        get() {
            return Database.connect(postgre.jdbcUrl, user = postgre.username, password = postgre.password)
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

    @AfterEach
    fun tearDown() {
        for (db in listOf(sourceDb, targetDb)) {
            transaction(db) {
                SchemaUtils.drop(TestData)
            }
        }
    }

    private fun insertUsers(num: Int) {
        for (i in 0 until num) {
            TestData.insert {
                it[name] = "Test$i"
                it[value] = "User$i"
            }
        }
    }
}
