package recce.server.dataset.datasource

import com.google.common.collect.Sets
import io.micronaut.context.ApplicationContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.testcontainers.containers.*
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import reactor.test.StepVerifier
import recce.server.dataset.DatasetRecService
import recce.server.recrun.MatchStatus
import java.util.stream.Stream

@Testcontainers
@Tag("requires-docker")
internal open class MultiDataSourceConnectivityIntegrationTest {
    companion object {
        @JvmStatic
        @Container
        protected val mysql = MySQLContainer<Nothing>("mysql:8")

        @JvmStatic
        @Container
        protected val mariadb = MariaDBContainer<Nothing>("mariadb:10")

        @JvmStatic
        @Container
        protected val postgres = PostgreSQLContainer<Nothing>("postgres:13-alpine")

        @JvmStatic
        @Container
        protected val mssql: MSSQLServerContainer<Nothing> =
            MSSQLServerContainer<Nothing>("mcr.microsoft.com/mssql/server:2019-latest").acceptLicense()

        private val databases = mapOf(
            "mysql" to mysql,
            "mariadb" to mariadb,
            "postgres" to postgres,
            "mssql" to mssql
        )

        @Suppress("UnstableApiUsage")
        private val databaseCombinations = Sets.combinations(databases.keys, 2)

        private lateinit var ctx: ApplicationContext

        @JvmStatic
        @BeforeAll
        fun startApplication() {
            val datasources = databases.flatMap { (name, container) ->
                listOf(
                    "r2dbc.datasources.$name.url" to container.jdbcUrl.replace("jdbc", "r2dbc:pool"),
                    "r2dbc.datasources.$name.username" to container.username,
                    "r2dbc.datasources.$name.password" to container.password
                )
            }.toMap()

            val datasets = databaseCombinations.map { Pair(it.first(), it.last()) }.flatMap { (source, target) ->
                listOf(
                    "reconciliation.datasets.$source-to-$target.source.dataSourceRef" to source,
                    "reconciliation.datasets.$source-to-$target.source.query" to "SELECT id as MigrationKey, name, value FROM TestData",
                    "reconciliation.datasets.$source-to-$target.target.dataSourceRef" to target,
                    "reconciliation.datasets.$source-to-$target.target.query" to "SELECT id as MigrationKey, name, value FROM TestData",
                )
            }.toMap()

            ctx = ApplicationContext.run(datasources + datasets)
        }

        @JvmStatic
        @AfterAll
        fun stopApplication() {
            ctx.stop()
        }

        private fun connectToJdbc(container: JdbcDatabaseContainer<Nothing>): Database {
            return Database.connect(container.jdbcUrl, user = container.username, password = container.password)
        }
    }

    @AfterEach
    fun `wipe databases`() {
        databases.forEach { transaction(connectToJdbc(it.value)) { SchemaUtils.drop(TestData) } }
    }

    protected object TestData : Table() {
        val id = integer("id").autoIncrement()
        val name = varchar("name", 255)
        val value = varchar("value", 255)

        override val primaryKey = PrimaryKey(id)
    }

    private fun createTestData(db: Database) {
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

    class DatabaseCombinations : ArgumentsProvider {
        @Suppress("UnstableApiUsage")
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> {
            return databaseCombinations.map { Arguments.of(*it.toTypedArray()) }.stream()
        }
    }

    @ParameterizedTest
    @ArgumentsSource(DatabaseCombinations::class)
    fun `should run rec between source and target`(source: String, target: String) {

        createTestData(
            connectToJdbc(
                databases[source] ?: throw IllegalArgumentException("Cannot find db type [$source].")
            )
        )

        createTestData(
            connectToJdbc(
                databases[target] ?: throw IllegalArgumentException("Cannot find db type [$target].")
            )
        )

        StepVerifier.create(ctx.getBean(DatasetRecService::class.java).runFor("$source-to-$target"))
            .assertNext { run ->
                assertThat(run.summary).usingRecursiveComparison().isEqualTo(
                    MatchStatus(
                        sourceOnly = 0,
                        targetOnly = 0,
                        bothMatched = 4,
                        bothMismatched = 0
                    )
                )
            }
            .verifyComplete()
    }
}
