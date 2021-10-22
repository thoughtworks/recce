package recce.server.config

import io.micronaut.context.ApplicationContext
import io.micronaut.data.r2dbc.operations.R2dbcOperations
import io.micronaut.inject.qualifiers.Qualifiers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.MSSQLServerContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@Testcontainers
internal open class MultiDataSourceConnectivityIntegrationTest {
    companion object {
        @JvmStatic
        @Container
        protected val mysql = MySQLContainer<Nothing>("mysql:8")

        @JvmStatic
        @Container
        protected val postgres = PostgreSQLContainer<Nothing>("postgres:13-alpine")

        @JvmStatic
        @Container
        protected val mssql: MSSQLServerContainer<Nothing> =
            MSSQLServerContainer<Nothing>("mcr.microsoft.com/mssql/server:2019-latest").acceptLicense()

        private val databases = mapOf(
            "mysql" to mysql,
            "postgres" to postgres,
            "mssql" to mssql
        )

        @JvmStatic
        fun testDatabases() = databases.keys

        private lateinit var ctx: ApplicationContext

        @JvmStatic
        @BeforeAll
        fun setupOnce() {
            ctx = ApplicationContext.run(
                databases.flatMap { (name, container) ->
                    listOf(
                        "r2dbc.datasources.$name.url" to container.jdbcUrl.replace("jdbc", "r2dbc:pool"),
                        "r2dbc.datasources.$name.username" to container.username,
                        "r2dbc.datasources.$name.password" to container.password
                    )
                }.toMap()
            )
        }

        @JvmStatic
        @AfterAll
        fun tearDownOnce() {
            ctx.stop()
        }

        private fun connectToJdbc(container: JdbcDatabaseContainer<Nothing>): Database {
            return Database.connect(container.jdbcUrl, user = container.username, password = container.password)
        }

        private fun connectToReactive(database: String) =
            ctx.getBean(R2dbcOperations::class.java, Qualifiers.byName(database)).connectionFactory().create()
    }

    protected object TestData : Table() {
        val id = integer("id").autoIncrement()
        val name = varchar("name", 255)
        val value = varchar("value", 255)

        override val primaryKey = PrimaryKey(id)
    }

    fun createTestData(db: Database) {
        transaction(db) {
            SchemaUtils.create(TestData)
            insertUsers(4)
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

    @ParameterizedTest
    @MethodSource("testDatabases")
    fun `should load data with r2dbc`(database: String) {

        createTestData(
            connectToJdbc(
                databases[database] ?: throw IllegalArgumentException("Cannot find db type [$database].")
            )
        )

        StepVerifier.create(getCount(database))
            .expectNext(4)
            .verifyComplete()
    }

    private fun getCount(database: String) = Mono.from(connectToReactive(database))
        .flatMapMany { it.createStatement("SELECT count(*) as count from TestData").execute() }
        .flatMap { result -> result.map { row, _ -> row.get("count") as Number } }
        .map { it.toLong() }
}
