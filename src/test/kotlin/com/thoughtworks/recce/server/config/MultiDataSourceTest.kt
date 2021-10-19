package com.thoughtworks.recce.server.config

import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
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
    // MSSQL setup requires the dependencies to be included for testcontainers. Apparently Test containers are not working for MSSQL with .apply function and
    // needs the micronaut settings to make it work. Since Micronaut creates separate containers for JDBC and R2DBC current setup does not support testing
    // through Micronaut. We need figure out a way to reuse containers in order to achieve this.
    // This is tracked as an issue https://github.com/testcontainers/testcontainers-java/issues/4473
    companion object {
        @JvmStatic
        @Container
        protected val mysql: MySQLContainer<Nothing> = MySQLContainer<Nothing>("mysql:8").apply {
            withDatabaseName("mysql")
            withUsername("sa")
            withPassword("pasword")
        }

        @JvmStatic
        @Container
        protected val postgre: PostgreSQLContainer<Nothing> = PostgreSQLContainer<Nothing>("postgres:13-alpine").apply {
            withDatabaseName("postgre")
            withUsername("sa")
            withPassword("pasword")
        }

//        @JvmStatic
//        @Container
//        protected val mssql: MSSQLServerContainer <Nothing> = MSSQLServerContainer<Nothing>("mcr.microsoft.com/mssql/server:2017-CU12").apply {
//            withDatabaseName("mssql")
//            withUsername("sa")
//            withPassword("pasword")
//        }.acceptLicense()
    }

    protected object TestData : Table() {
        val id = integer("id").autoIncrement()
        val name = varchar("name", 255)
        val value = varchar("value", 255)

        override val primaryKey = PrimaryKey(id)
    }

    protected val sourceDb: Database
        get() {
            return getDatabase(currentSource)
        }

    protected val targetDb: Database
        get() {
            return getDatabase(currentTarget)
        }

    private var currentSource = sources.NA
    private var currentTarget = sources.NA

    fun getDatabase(source: sources): Database {
        if (source == sources.PostGre)
            return Database.connect(postgre.jdbcUrl, user = postgre.username, password = postgre.password)
//        else if(source == sources.MSSql)
//            return Database.connect(mssql.jdbcUrl, user = mssql.username, password = mssql.password)
        else
            return Database.connect(mysql.jdbcUrl, user = mysql.username, password = mysql.password)
    }

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

    fun getConnectionFactory(source: sources): ConnectionFactory {
        if (source == sources.MySQL) {
            return ConnectionFactories.get(
                ConnectionFactoryOptions.parse(mysql.jdbcUrl.replace("jdbc", "r2dbc"))
                    .mutate()
                    .option(ConnectionFactoryOptions.USER, mysql.username)
                    .option(ConnectionFactoryOptions.PASSWORD, mysql.password)
                    .build()
            )
        }
//        else if(source == sources.MSSql)
//        {
//            return ConnectionFactories.get(
//                ConnectionFactoryOptions.parse(mssql.jdbcUrl.replace("jdbc", "r2dbc"))
//                    .mutate()
//                    .option(ConnectionFactoryOptions.USER, mssql.username)
//                    .option(ConnectionFactoryOptions.PASSWORD, mssql.password)
//                    .build());
//        }
        else
            return ConnectionFactories.get(
                ConnectionFactoryOptions.parse(postgre.jdbcUrl.replace("jdbc", "r2dbc"))
                    .mutate()
                    .option(ConnectionFactoryOptions.USER, postgre.username)
                    .option(ConnectionFactoryOptions.PASSWORD, postgre.password)
                    .build()
            )
    }

    @Test
    fun `should load data from Mysql & postgre`() {
        this.currentSource = sources.MySQL
        this.currentTarget = sources.PostGre
        setup()
        StepVerifier.create(getCount(getConnectionFactory(currentSource)))
            .expectNext(3)
            .verifyComplete()

        StepVerifier.create(getCount(getConnectionFactory(currentTarget)))
            .expectNext(4)
            .verifyComplete()
    }

    private fun getCount(connectionFactory: ConnectionFactory) =
        Mono.from(connectionFactory.create())
            .flatMapMany { it.createStatement("SELECT count(*) as count from TestData").execute() }
            .flatMap { result -> result.map { row, _ -> row.get("count") as Long } }
}

internal enum class sources {
    NA,
    MySQL,
    PostGre,
    MSSql
}
