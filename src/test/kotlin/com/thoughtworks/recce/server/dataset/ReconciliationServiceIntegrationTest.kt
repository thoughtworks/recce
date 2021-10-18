package com.thoughtworks.recce.server.dataset

import com.thoughtworks.recce.server.config.DataSourceTest
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.r2dbc.spi.R2dbcBadGrammarException
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import reactor.kotlin.core.util.function.*
import reactor.test.StepVerifier
import reactor.util.function.Tuples

@MicronautTest(
    environments = arrayOf("test-integration"),
    propertySources = arrayOf("classpath:config/application-test-dataset.yml")
)
class ReconciliationServiceIntegrationTest : DataSourceTest() {
    @Inject
    lateinit var service: ReconciliationService

    @Inject
    lateinit var runRepository: MigrationRunRepository

    @Inject
    lateinit var recordRepository: MigrationRecordRepository

    @AfterEach
    override fun tearDown() {
        super.tearDown()
        runRepository.deleteAll().block()
    }

    @Test
    fun `start can stream a source dataset`() {
        StepVerifier.create(service.runFor("test-dataset"))
            .assertNext { run ->
                assertThat(run.id).isNotNull
                assertThat(run.dataSetId).isEqualTo("test-dataset")
                assertThat(run.createdTime).isNotNull
                assertThat(run.updatedTime).isAfterOrEqualTo(run.createdTime)
                assertThat(run.completedTime).isAfterOrEqualTo(run.createdTime)
                assertThat(run.results).isEqualTo(DataSetResults(3))
            }
            .verifyComplete()

        StepVerifier.create(
            runRepository.findAll()
                .flatMap { run ->
                    recordRepository.findByIdMigrationId(run.id!!).map { Tuples.of(run, it) }
                }
        )
            .assertNext { (run, record) ->
                assertThat(run.dataSetId).isEqualTo("test-dataset")
                assertThat(record.id.migrationId).isEqualTo(run.id)
                assertThat(record.id.migrationKey).isEqualTo("1")
                assertThat(record.sourceData).isEqualTo("88aa59c134b8a7e484f77340ae745df5d8e0434b5fef012af499ed002cb63b78")
            }
            .assertNext { (run, record) ->
                assertThat(run.dataSetId).isEqualTo("test-dataset")
                assertThat(record.id.migrationId).isEqualTo(run.id)
                assertThat(record.id.migrationKey).isEqualTo("2")
                assertThat(record.sourceData).isEqualTo("8bc642e12847c144c236eadfeef828491f94871b194902972dc72f759b78def8")
            }
            .assertNext { (run, record) ->
                assertThat(run.dataSetId).isEqualTo("test-dataset")
                assertThat(record.id.migrationId).isEqualTo(run.id)
                assertThat(record.id.migrationKey).isEqualTo("3")
                assertThat(record.sourceData).isEqualTo("94a226822921b49fb04c55f7fb6e862978eeaadbefed44a5cbcc7eb7cc210124")
            }
            .verifyComplete()
    }

    @Test
    fun `should emit error on bad query`() {
        transaction(sourceDb) { SchemaUtils.drop(TestData) }

        StepVerifier.create(service.runFor("test-dataset"))
            .expectError(R2dbcBadGrammarException::class.java)
            .verify()
    }

    @Test
    fun `triggering multiple recs ignores failures`() {
        StepVerifier.create(service.runIgnoreFailure(listOf("bad", "test-dataset", "bad2")))
            .assertNext { assertThat(it.dataSetId).isEqualTo("test-dataset") }
            .verifyComplete()
    }
}
