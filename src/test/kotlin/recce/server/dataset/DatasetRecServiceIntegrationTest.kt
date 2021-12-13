package recce.server.dataset

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.r2dbc.spi.R2dbcBadGrammarException
import jakarta.inject.Inject
import jakarta.inject.Named
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2
import reactor.test.StepVerifier
import reactor.util.function.Tuples
import recce.server.dataset.datasource.flywayCleanMigrate
import recce.server.recrun.*
import java.nio.file.Path
import javax.sql.DataSource

@MicronautTest(
    environments = arrayOf("test-integration"),
    propertySources = arrayOf("classpath:config/application-test-dataset.yml")
)
class DatasetRecServiceIntegrationTest {

    @TempDir lateinit var tempDir: Path
    @Inject @field:Named("source-h2") lateinit var sourceDataSource: DataSource
    @Inject @field:Named("target-h2") lateinit var targetDataSource: DataSource

    @Inject lateinit var service: DatasetRecService
    @Inject lateinit var runRepository: RecRunRepository
    @Inject lateinit var recordRepository: RecRecordRepository

    @BeforeEach
    fun setup() {
        val createTable = """
            CREATE TABLE TestData (
                name VARCHAR(255) PRIMARY KEY NOT NULL,
                value VARCHAR(255) NOT NULL
            );
        """.trimMargin()

        val insertUser: (Int) -> String = { i ->
            """
            INSERT INTO TestData (name, value) 
            VALUES ('Test$i', 'User$i');
            """.trimIndent()
        }

        val sourceSql = createTable + (0..2).joinToString("\n", transform = insertUser)
        val targetSql = createTable + ((0..1) + (3..4)).joinToString("\n", transform = insertUser)
        flywayCleanMigrate(tempDir, sourceSql, sourceDataSource)
        flywayCleanMigrate(tempDir, targetSql, targetDataSource)
    }

    @AfterEach
    fun tearDown() {
        runRepository.deleteAll().block()
    }

    @Test
    fun `can run a simple reconciliation`() {
        StepVerifier.create(service.runFor("test-dataset"))
            .assertNext { run ->
                checkPersistentFieldsFor(run)
            }
            .verifyComplete()

        StepVerifier.create(
            runRepository.findAll()
                .flatMap { run ->
                    recordRepository.findByRecRunId(run.id!!).map { Tuples.of(run, it) }
                }
        )
            .assertNext { (run, record) ->
                checkPersistentFieldsFor(run)
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

    private fun checkPersistentFieldsFor(run: RecRun) {
        assertThat(run.id).isNotNull
        assertThat(run.datasetId).isEqualTo("test-dataset")
        assertThat(run.createdTime).isNotNull
        assertThat(run.updatedTime).isAfterOrEqualTo(run.createdTime)
        assertThat(run.completedTime).isAfterOrEqualTo(run.createdTime)
        assertThat(run.status).isEqualTo(RunStatus.Successful)
        assertThat(run.summary).isEqualTo(MatchStatus(1, 2, 2, 0))
        val expectedMeta = DatasetMeta(
            listOf(
                ColMeta("MIGRATIONKEY", "String"),
                ColMeta("NAME", "String"),
                ColMeta("VALUE", "String")
            )
        )
        assertThat(run.sourceMeta).usingRecursiveComparison().isEqualTo(expectedMeta)
        assertThat(run.targetMeta).usingRecursiveComparison().isEqualTo(expectedMeta)
    }

    @Test
    fun `should emit error on bad source query`() {
        flywayCleanMigrate(tempDir, "SELECT 1", sourceDataSource)

        StepVerifier.create(service.runFor("test-dataset"))
            .consumeErrorWith {
                assertThat(it)
                    .isExactlyInstanceOf(DataLoadException::class.java)
                    .hasMessageContaining("Failed to load data from source")
                    .hasMessageContaining("\"TESTDATA\" not found")
                    .hasCauseExactlyInstanceOf(R2dbcBadGrammarException::class.java)
            }
            .verify()
    }

    @Test
    fun `should emit error on bad target query`() {
        flywayCleanMigrate(tempDir, "SELECT 1", targetDataSource)

        StepVerifier.create(service.runFor("test-dataset"))
            .consumeErrorWith {
                assertThat(it)
                    .isExactlyInstanceOf(DataLoadException::class.java)
                    .hasMessageContaining("Failed to load data from target")
                    .hasMessageContaining("\"TESTDATA\" not found")
                    .hasCauseExactlyInstanceOf(R2dbcBadGrammarException::class.java)
            }
            .verify()
    }
}
