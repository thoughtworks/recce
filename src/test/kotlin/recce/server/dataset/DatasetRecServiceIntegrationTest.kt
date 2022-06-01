package recce.server.dataset

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.r2dbc.spi.R2dbcBadGrammarException
import jakarta.inject.Inject
import jakarta.inject.Named
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2
import reactor.kotlin.test.test
import reactor.util.function.Tuples
import recce.server.BuildInfoConfiguration
import recce.server.R2dbcDatasource
import recce.server.RecConfiguration
import recce.server.dataset.datasource.flywayCleanMigrate
import recce.server.recrun.*
import java.nio.file.Path
import java.util.function.Consumer
import javax.sql.DataSource

@MicronautTest(
    environments = arrayOf("test-integration"),
    propertySources = arrayOf("classpath:config/application-test-dataset.yml")
)
class DatasetRecServiceIntegrationTest {

    @TempDir
    lateinit var tempDir: Path
    @Inject
    @field:Named("source-h2")
    lateinit var sourceDataSource: DataSource
    @Inject
    @field:Named("target-h2")
    lateinit var targetDataSource: DataSource

    @Inject
    lateinit var ctx: ApplicationContext
    @Inject
    lateinit var service: DatasetRecService
    @Inject
    lateinit var runRepository: RecRunRepository
    @Inject
    lateinit var recordRepository: RecRecordRepository

    @BeforeEach
    fun setup() {
        val createTable = """
            CREATE TABLE TestData (
                name VARCHAR(255) PRIMARY KEY NOT NULL,
                val VARCHAR(255) NOT NULL
            );
        """.trimMargin()

        val insertUser: (Int) -> String = { i ->
            """
            INSERT INTO TestData (name, val) 
            VALUES ('Test$i', 'User$i');
            """.trimIndent()
        }

        val sourceSql = createTable + (0..2).joinToString("\n", transform = insertUser)
        val targetSql = createTable + ((0..1) + (3..4)).joinToString("\n", transform = insertUser)
        flywayCleanMigrate(tempDir, sourceSql, sourceDataSource)
        flywayCleanMigrate(tempDir, targetSql, targetDataSource)
    }

    @BeforeEach
    @AfterEach
    fun wipeDb() {
        runRepository.deleteAll().block()
    }

    @Test
    fun `can run a simple reconciliation`() {
        service.runFor("test-dataset")
            .test()
            .assertNext { run ->
                checkCompleted(run)
                checkSuccessful(run)
            }
            .verifyComplete()

        runRepository.findAll()
            .flatMap { run ->
                recordRepository.findByRecRunId(run.id!!).map { Tuples.of(run, it) }
            }
            .test()
            .assertNext { (run, record) ->
                checkCompleted(run)
                checkSuccessful(run)
                assertThat(record.key.recRunId).isEqualTo(run.id)
                assertThat(record.key.migrationKey).isEqualTo("Test0")
                assertThat(record.sourceData).isEqualTo(record.targetData).`is`(hexSha256Hash)
            }
            .assertNext { (run, record) ->
                assertThat(run.datasetId).isEqualTo("test-dataset")
                assertThat(record.key.recRunId).isEqualTo(run.id)
                assertThat(record.key.migrationKey).isEqualTo("Test1")
                assertThat(record.sourceData).isEqualTo(record.targetData).`is`(hexSha256Hash)
            }
            .assertNext { (run, record) ->
                assertThat(run.datasetId).isEqualTo("test-dataset")
                assertThat(record.key.recRunId).isEqualTo(run.id)
                assertThat(record.key.migrationKey).isEqualTo("Test2")
                assertThat(record.sourceData).`is`(hexSha256Hash)
                assertThat(record.targetData).isNull()
            }
            .assertNext { (run, record) ->
                assertThat(run.datasetId).isEqualTo("test-dataset")
                assertThat(record.key.recRunId).isEqualTo(run.id)
                assertThat(record.key.migrationKey).isEqualTo("Test3")
                assertThat(record.sourceData).isNull()
                assertThat(record.targetData).`is`(hexSha256Hash)
            }
            .assertNext { (run, record) ->
                assertThat(run.datasetId).isEqualTo("test-dataset")
                assertThat(record.key.recRunId).isEqualTo(run.id)
                assertThat(record.key.migrationKey).isEqualTo("Test4")
                assertThat(record.sourceData).isNull()
                assertThat(record.targetData).`is`(hexSha256Hash)
            }
            .verifyComplete()
    }

    private fun checkCompleted(run: RecRun) {
        val datasetConfig = ctx.getBean(RecConfiguration::class.java).datasets["test-dataset"]
        val buildConfig = ctx.getBean(BuildInfoConfiguration::class.java)
        val sourceR2dbcConfig = ctx.getBean(R2dbcDatasource::class.java, Qualifiers.byName("source-h2"))
        val targetR2dbcConfig = ctx.getBean(R2dbcDatasource::class.java, Qualifiers.byName("target-h2"))

        val expectedMeta = mapOf(
            "sourceQuery" to datasetConfig?.source?.queryStatement,
            "targetQuery" to datasetConfig?.target?.queryStatement,
            "sourceUrl" to sourceR2dbcConfig.url,
            "targetUrl" to targetR2dbcConfig.url,
            "version" to buildConfig.version
        )

        SoftAssertions.assertSoftly { softly ->
            softly.assertThat(run.id).isNotNull
            softly.assertThat(run.datasetId).isEqualTo("test-dataset")
            softly.assertThat(run.createdTime).isNotNull
            softly.assertThat(run.updatedTime).isAfterOrEqualTo(run.createdTime)
            softly.assertThat(run.completedTime).isAfterOrEqualTo(run.createdTime)
            softly.assertThat(run.metadata).usingRecursiveComparison().isEqualTo(expectedMeta)
        }
    }

    private fun checkSuccessful(run: RecRun) {
        SoftAssertions.assertSoftly { softly ->
            softly.assertThat(run.status).isEqualTo(RunStatus.Successful)
            softly.assertThat(run.failureCause).isNull()
            softly.assertThat(run.summary).isEqualTo(MatchStatus(1, 2, 2, 0))
            val expectedMeta = DatasetMeta(
                listOf(
                    ColMeta("MIGRATIONKEY", "String"),
                    ColMeta("NAME", "String"),
                    ColMeta("VAL", "String")
                )
            )
            softly.assertThat(run.sourceMeta).usingRecursiveComparison().isEqualTo(expectedMeta)
            softly.assertThat(run.targetMeta).usingRecursiveComparison().isEqualTo(expectedMeta)
        }
    }

    private fun checkFailed(run: RecRun) {
        SoftAssertions.assertSoftly { softly ->
            softly.assertThat(run.status).isEqualTo(RunStatus.Failed)
            softly.assertThat(run.summary).satisfiesAnyOf(
                Consumer
                { st -> assertThat(st).isNull() },
                { st -> assertThat(st).isEqualTo(MatchStatus()) }
            )
            softly.assertThat(run.sourceMeta).isEqualTo(DatasetMeta())
            softly.assertThat(run.targetMeta).isEqualTo(DatasetMeta())
        }
    }

    @Test
    fun `should fail run on bad source query`() {
        // Wipe the source DB
        flywayCleanMigrate(tempDir, "SELECT 1", sourceDataSource)

        service.runFor("test-dataset")
            .test()
            .assertNext { run ->
                checkCompleted(run)
                checkFailed(run)
                assertThat(run.failureCause)
                    .isExactlyInstanceOf(DataLoadException::class.java)
                    .hasMessageContaining("Failed to load data from Source(ref=source-h2)")
                    .hasMessageContaining("\"TESTDATA\" not found")
                    .hasCauseExactlyInstanceOf(R2dbcBadGrammarException::class.java)
            }
            .verifyComplete()

        // Check persisted representation
        runRepository.findAll()
            .test()
            .assertNext { run ->
                checkCompleted(run)
                checkFailed(run)
            }
            .verifyComplete()
    }

    @Test
    fun `should fail run on bad target query`() {
        // Wipe the target DB
        flywayCleanMigrate(tempDir, "SELECT 1", targetDataSource)

        service.runFor("test-dataset")
            .test()
            .assertNext { run ->
                checkCompleted(run)
                checkFailed(run)
                assertThat(run.failureCause)
                    .isExactlyInstanceOf(DataLoadException::class.java)
                    .hasMessageContaining("Failed to load data from Target(ref=target-h2)")
                    .hasMessageContaining("\"TESTDATA\" not found")
                    .hasCauseExactlyInstanceOf(R2dbcBadGrammarException::class.java)
            }
            .verifyComplete()

        // Check persisted representation
        runRepository.findAll()
            .test()
            .assertNext { run ->
                checkCompleted(run)
                checkFailed(run)
            }
            .verifyComplete()
    }
}
