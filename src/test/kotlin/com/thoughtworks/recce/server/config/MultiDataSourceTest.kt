package com.thoughtworks.recce.server.config

import com.thoughtworks.recce.server.config.DataSourceTest.TestData.autoIncrement
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@Testcontainers
// @MicronautTest(environments = arrayOf("test-multisource"))
internal class MultiDataSourceTest {

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
        protected val postgre: PostgreSQLContainer<Nothing> = PostgreSQLContainer<Nothing>("postgres:13-alpine").apply {
            withDatabaseName("targetdb")
            withUsername("sa")
            withPassword("pasword")
        }
    }

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
