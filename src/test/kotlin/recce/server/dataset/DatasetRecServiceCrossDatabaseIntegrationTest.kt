package recce.server.dataset

import io.micronaut.context.ApplicationContext
import org.assertj.core.api.Assertions.assertThat
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
import org.testcontainers.junit.jupiter.Testcontainers
import reactor.kotlin.test.test
import recce.server.dataset.datasource.DbDescriptor
import recce.server.dataset.datasource.flywayCleanMigrate
import recce.server.recrun.MatchStatus
import java.nio.file.Path
import java.util.concurrent.CompletableFuture.allOf
import java.util.concurrent.CompletableFuture.runAsync
import java.util.stream.IntStream
import java.util.stream.Stream

@Tag("slow")
@Testcontainers(disabledWithoutDocker = true)
internal open class DatasetRecServiceCrossDatabaseIntegrationTest {
    companion object {
        /**
         * Databases which we expect to produce matching hashes for values of similar types.
         */
        private val databases: Map<String, JdbcDatabaseContainer<Nothing>> = mapOf(
            "mssql" to MSSQLServerContainer<Nothing>("mcr.microsoft.com/mssql/server:2019-latest").acceptLicense(),
            "mysql" to MySQLContainer("mysql:8"),
            "mariadb" to MariaDBContainer("mariadb:10.5"),
            "postgres" to PostgreSQLContainer("postgres:14-alpine"),
        )

        /**
         * Types we want to test for each database combination
         */
        private val sqlTypesToValues = listOf(
            "SMALLINT" to "1",
            "INTEGER" to "1",
            "BIGINT" to "1",
            "VARCHAR(4)" to "'text'",
            "TEXT" to "'text'",
        )

        /**
         * Create pairs of databases to test against. We don't have to test against all possibly combinations
         * because we can reasonably conclude that the matching is transitive, i.e if A==B and B==C then A==C.
         */
        private val databaseCombinations = IntStream
            .range(1, databases.keys.size)
            .mapToObj { databases.keys.toList()[it - 1] to databases.keys.toList()[it] }
            .toList()

        private fun containerFor(name: String) =
            databases[name] ?: throw IllegalArgumentException("Cannot find db type [$name].")

        private lateinit var ctx: ApplicationContext

        @JvmStatic
        @BeforeAll
        fun startDatabases() {
            databases.values.parallelStream().forEach { it.start() }
        }

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

            val datasets = databaseCombinations.flatMap { (source, target) ->
                listOf(
                    "reconciliation.datasets.$source-to-$target.source.datasourceRef" to source,
                    "reconciliation.datasets.$source-to-$target.source.queryConfig.query" to "SELECT id AS MigrationKey, value FROM TestData",
                    "reconciliation.datasets.$source-to-$target.target.datasourceRef" to target,
                    "reconciliation.datasets.$source-to-$target.target.queryConfig.query" to "SELECT id AS MigrationKey, value FROM TestData",
                )
            }.toMap()

            ctx = ApplicationContext.run(datasources + datasets)
        }

        @JvmStatic
        @AfterAll
        fun stopApplication() {
            ctx.stop()
            databases.values.parallelStream().forEach { it.stop() }
        }
    }

    @TempDir
    lateinit var tempDir: Path

    private fun createTestData(scenario: ScenarioConfig) {
        val sql = """
            CREATE TABLE TestData
            (
                id             INT PRIMARY KEY,
                value          ${scenario.sqlType}
            );
            
            INSERT INTO TestData (id, value) VALUES (1, ${scenario.sqlValue});
            """

        try {
            flywayCleanMigrate(tempDir, sql, scenario.dbDescriptor)
        } catch (e: Exception) {
            throw RuntimeException("Failed to create test schema on [${scenario.db}] for $scenario", e)
        }
    }

    private class TestScenarios : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> {
            return databaseCombinations
                .flatMap { (source, target) ->
                    sqlTypesToValues.map { (sqlType, sqlValue) ->
                        ScenarioConfig(source, sqlType, sqlValue) to ScenarioConfig(target, sqlType, sqlValue)
                    }
                }
                .map { (source, target) -> Arguments.of(source, target) }
                .stream()
        }
    }

    data class ScenarioConfig(
        val db: String,
        val sqlType: String,
        val sqlValue: String
    ) {
        val dbDescriptor by lazy { containerFor(db).let { DbDescriptor(it.jdbcUrl, it.username, it.password) } }
    }

    @ParameterizedTest
    @ArgumentsSource(TestScenarios::class)
    fun `rows match between source and target`(source: ScenarioConfig, target: ScenarioConfig) {

        allOf(
            runAsync { createTestData(source) },
            runAsync { createTestData(target) }
        ).join()

        ctx.getBean(DatasetRecService::class.java).runFor("${source.db}-to-${target.db}")
            .test()
            .assertNext { run ->
                assertThat(run.summary)
                    .describedAs("Values between two DBs should match. sourceMeta=${run.sourceMeta} targetMeta=${run.targetMeta}")
                    .usingRecursiveComparison()
                    .isEqualTo(
                        MatchStatus(bothMatched = 1)
                    )
            }
            .verifyComplete()
    }
}
