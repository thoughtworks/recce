package com.thoughtworks.recce.server.dataset

import com.thoughtworks.recce.server.config.DataSourceTest
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.r2dbc.spi.R2dbcBadGrammarException
import jakarta.inject.Inject
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import reactor.test.StepVerifier

internal class DataSetServiceTest {
    @Test
    fun `start should throw on missing dataset`() {
        Assertions.assertThatThrownBy { DataSetService(mock(), mock(), mock()).start("test-dataset") }
            .isExactlyInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("test-dataset")
    }
}

@MicronautTest(
    environments = arrayOf("test-integration"),
    propertySources = arrayOf("classpath:config/application-test-dataset.yml")
)
class DataSetServiceIntegrationTest : DataSourceTest() {
    @Inject
    lateinit var service: DataSetService

    @Inject
    lateinit var runRepository: MigrationRunRepository

    @Inject
    lateinit var recordRepository: MigrationRecordRepository

    @Test
    fun `start can stream a source dataset`() {
        StepVerifier.create(service.start("test-dataset"))
            .assertNext { run ->
                assertThat(run.id).isNotNull
                assertThat(run.dataSetId).isEqualTo("test-dataset")
                assertThat(run.createdTime).isNotNull
                assertThat(run.updatedTime).isNotNull
                assertThat(run.completedTime).isNull()
                assertThat(run.results).isEqualTo(DataSetResults(1))
            }
            .verifyComplete()

        val migrationRuns = runRepository.findAll()

        StepVerifier.create(migrationRuns)
            .assertNext {
                assertThat(it.dataSetId).isEqualTo("test-dataset")
            }
            .verifyComplete()

        StepVerifier.create(migrationRuns.flatMap { run -> recordRepository.findByIdMigrationId(run.id!!) })
            .assertNext { record ->
                assertThat(record.id.migrationId).isNotNull
                assertThat(record.id.migrationKey).isEqualTo("sourcedatacount")
                assertThat(record.sourceData).isEqualTo("b57448e19e0e383cdabaf669a4b85676bb7061e7f3720e57ea148a5735de957a")
            }
            .verifyComplete()
    }

    @Test
    fun `should emit error on bad query`() {
        transaction(sourceDb) { SchemaUtils.drop(TestData) }

        StepVerifier.create(service.start("test-dataset"))
            .expectError(R2dbcBadGrammarException::class.java)
            .verify()
    }
}
