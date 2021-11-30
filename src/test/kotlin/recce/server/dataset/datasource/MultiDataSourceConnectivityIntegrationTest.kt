package recce.server.dataset.datasource

import com.google.common.collect.Sets
import io.micronaut.context.ApplicationContext
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.io.TempDir
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
import java.nio.file.Files
import java.nio.file.Path
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
        private fun containerFor(name: String) = databases[name] ?: throw IllegalArgumentException("Cannot find db type [$name].")

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
                    "reconciliation.datasets.$source-to-$target.source.query" to "SELECT id as MigrationKey, value FROM TestData",
                    "reconciliation.datasets.$source-to-$target.target.dataSourceRef" to target,
                    "reconciliation.datasets.$source-to-$target.target.query" to "SELECT id as MigrationKey, value FROM TestData",
                )
            }.toMap()

            ctx = ApplicationContext.run(datasources + datasets)
        }

        @JvmStatic
        @AfterAll
        fun stopApplication() {
            ctx.stop()
        }
    }

    @TempDir
    lateinit var tempDir: Path

    private fun createTestData(scenario: ScenarioConfig) {
        Files.writeString(
            tempDir.resolve("V1__CREATE.sql"),
            """
            CREATE TABLE TestData
            (
                id             INT PRIMARY KEY,
                value          ${scenario.sqlType}
            );
            
            INSERT INTO TestData (id, value) VALUES (1, ${scenario.sqlValue});
            """.trimIndent()
        )

        val flyway = Flyway.configure()
            .dataSource(scenario.dbContainer.jdbcUrl, scenario.dbContainer.username, scenario.dbContainer.password)
            .locations("filesystem:$tempDir")
            .load()
        flyway.clean()
        flyway.migrate()
    }

    class DatabaseCombinations : ArgumentsProvider {
        @Suppress("UnstableApiUsage")
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> {
            return databaseCombinations.map { Arguments.of(
                ScenarioConfig(db = it.first(), sqlType = "VARCHAR(255)", sqlValue = "'User0'"),
                ScenarioConfig(db = it.last(), sqlType = "VARCHAR(255)", sqlValue = "'User0'")
            ) }.stream()
        }
    }

    data class ScenarioConfig(
        val db: String,
        val sqlType: String,
        val sqlValue: String
    ) {
        val dbContainer: JdbcDatabaseContainer<Nothing> by lazy { containerFor(db) }
    }

    @ParameterizedTest
    @ArgumentsSource(DatabaseCombinations::class)
    fun `should run rec between source and target`(source: ScenarioConfig, target: ScenarioConfig) {

        createTestData(source)
        createTestData(target)

        StepVerifier.create(ctx.getBean(DatasetRecService::class.java).runFor("${source.db}-to-${target.db}"))
            .assertNext { run ->
                assertThat(run.summary).usingRecursiveComparison().isEqualTo(
                    MatchStatus(
                        sourceOnly = 0,
                        targetOnly = 0,
                        bothMatched = 1,
                        bothMismatched = 0
                    )
                )
            }
            .verifyComplete()
    }
}
