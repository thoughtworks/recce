package recce.server.api

import io.micronaut.context.annotation.Replaces
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.restassured.module.kotlin.extensions.Extract
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import io.restassured.response.ValidatableResponse
import io.restassured.specification.RequestSpecification
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.apache.http.HttpStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import recce.server.dataset.DatasetRecRunner
import recce.server.recrun.*
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val notFoundId = 0
private val testDataset = "testDataset"
private val testCompletedDuration = Duration.ofMinutes(3).plusNanos(234)
private val testResults = RecRun(
    id = 12,
    datasetId = testDataset,
    createdTime = LocalDateTime.of(2021, 10, 25, 16, 16, 16).toInstant(ZoneOffset.UTC),
).apply {
    completedTime = createdTime?.plusNanos(testCompletedDuration.toNanos())
    updatedTime = completedTime?.plusSeconds(10)
    sourceMeta = DatasetMeta(listOf(ColMeta("test1", "String")))
    targetMeta = DatasetMeta(listOf(ColMeta("test1", "String")))
    summary = MatchStatus(1, 2, 3, 4)
}

private val testRecRecord = RecRecord(RecRecordKey(testResults.id!!, "key"))

private val service = mock<DatasetRecRunner> {
    on { runFor(eq(testDataset)) } doReturn Mono.just(testResults)
}

private val runRepository = mock<RecRunRepository> {
    on { findById(testResults.id!!) } doReturn Mono.just(testResults)
    on { existsById(testResults.id!!) } doReturn Mono.just(true)
    on { findById(notFoundId) } doReturn Mono.empty()
    on { existsById(notFoundId) } doReturn Mono.just(false)
    on { findTop10ByDatasetIdOrderByCompletedTimeDesc(testDataset) } doReturn Flux.just(testResults, testResults)
}

private fun recordRepository(sampleRecords: List<RecRecord>) = mock<RecRecordRepository> {
    on { findFirst10ByRecRunIdAndSourceDataIsNull(testResults.id!!) } doReturn Flux.fromIterable(sampleRecords)
    on { findFirst10ByRecRunIdAndTargetDataIsNull(testResults.id!!) } doReturn Flux.fromIterable(sampleRecords)
    on { findFirst10ByRecRunIdAndSourceDataNotEqualsTargetData(testResults.id!!) } doReturn Flux.fromIterable(sampleRecords)
}

internal class DatasetRecRunControllerTest {
    private val controller = DatasetRecRunController(service, runRepository, recordRepository(emptyList()))

    @Test
    fun `can get run by id`() {
        StepVerifier.create(controller.get(testResults.id!!))
            .assertNext(::assertThatModelMatchesTestResults)
            .verifyComplete()
    }

    @Test
    fun `can get run by id with sample bad rows`() {
        val controller = DatasetRecRunController(service, runRepository, recordRepository(listOf(testRecRecord, testRecRecord)))
        StepVerifier.create(controller.get(testResults.id!!))
            .assertNext {
                assertThatModelMatchesTestResults(it)
                assertThat(it.summary?.source?.onlyMigrationKeySample).hasSize(2)
                assertThat(it.summary?.target?.onlyMigrationKeySample).hasSize(2)
                assertThat(it.summary?.bothMismatchedMigrationKeySample).hasSize(2)
            }
            .verifyComplete()
    }

    private fun assertThatModelMatchesTestResults(apiModel: RunApiModel) {
        SoftAssertions.assertSoftly {
            assertThat(apiModel.id).isEqualTo(testResults.id)
            assertThat(apiModel.datasetId).isEqualTo(testResults.datasetId)
            assertThat(apiModel.createdTime).isEqualTo(testResults.createdTime)
            assertThat(apiModel.completedTime).isEqualTo(testResults.completedTime)
            assertThat(apiModel.summary?.totalRowCount).isEqualTo(testResults.summary?.total)
            assertThat(apiModel.summary?.bothMatchedCount).isEqualTo(testResults.summary?.bothMatched)
            assertThat(apiModel.summary?.bothMismatchedCount).isEqualTo(testResults.summary?.bothMismatched)
            assertThat(apiModel.summary?.source?.totalRowCount).isEqualTo(testResults.summary?.sourceTotal)
            assertThat(apiModel.summary?.source?.onlyHereCount).isEqualTo(testResults.summary?.sourceOnly)
            assertThat(apiModel.summary?.source?.meta).isEqualTo(testResults.sourceMeta)
            assertThat(apiModel.summary?.target?.totalRowCount).isEqualTo(testResults.summary?.targetTotal)
            assertThat(apiModel.summary?.target?.onlyHereCount).isEqualTo(testResults.summary?.targetOnly)
            assertThat(apiModel.summary?.target?.meta).isEqualTo(testResults.targetMeta)
        }
    }

    @Test
    fun `can get runs by dataset id`() {
        StepVerifier.create(controller.get(testDataset))
            .assertNext(::assertThatModelMatchesTestResults)
            .assertNext(::assertThatModelMatchesTestResults)
            .verifyComplete()
    }

    @Test
    fun `create should delegate to service`() {
        StepVerifier.create(controller.create(DatasetRecRunController.RunCreationParams(eq(testDataset))))
            .assertNext(::assertThatModelMatchesTestResults)
            .verifyComplete()
    }
}

@MicronautTest
internal class DatasetRecRunControllerApiTest {

    @Inject
    lateinit var spec: RequestSpecification

    @Test
    fun `can get run by id`() {
        Given {
            spec(spec)
        } When {
            get("/runs/${testResults.id!!}")
        } Then {
            validateTestResultsAreReturned()
        }
    }

    @Test
    fun `get 404 on run by id when not found`() {
        Given {
            spec(spec)
        } When {
            get("/runs/$notFoundId")
        } Then {
            statusCode(HttpStatus.SC_NOT_FOUND)
            body("message", equalTo("Not Found"))
        }
    }

    @Test
    fun `create should delegate to service`() {
        Given {
            spec(spec)
        } When {
            body(mapOf("datasetId" to testDataset))
            post("/runs")
        } Then {
            validateTestResultsAreReturned()
        }
    }

    private fun ValidatableResponse.validateTestResultsAreReturned() {
        statusCode(HttpStatus.SC_OK)
        body("datasetId", equalTo(testDataset))
        body("id", equalTo(testResults.id))
        body("createdTime", equalTo(DateTimeFormatter.ISO_INSTANT.format(testResults.createdTime)))
        body("completedTime", equalTo(DateTimeFormatter.ISO_INSTANT.format(testResults.completedTime)))
        body("completedDurationSeconds", closeTo(testCompletedDuration.toSeconds().toDouble(), 0.00001))
        body(
            "summary",
            allOf(
                hasEntry("totalRowCount", 10),
                hasEntry("bothMatchedCount", 3),
                hasEntry("bothMismatchedCount", 4),
            )
        )
        body(
            "summary.source",
            allOf(
                hasEntry("totalRowCount", 8),
                hasEntry("onlyHereCount", 1),
            )
        )
        body("summary.source.meta.cols", equalTo(listOf(mapOf("name" to "test1", "javaType" to "String"))))
        body(
            "summary.target",
            allOf(
                hasEntry("totalRowCount", 9),
                hasEntry("onlyHereCount", 2),
            )
        )
        body("summary.target.meta.cols", equalTo(listOf(mapOf("name" to "test1", "javaType" to "String"))))
    }

    @Test
    fun `create should validate params`() {
        val errors: List<Map<String, String>> =
            Given {
                spec(spec)
            } When {
                body(emptyMap<String, String>())
                post("/runs")
            } Then {
                statusCode(HttpStatus.SC_BAD_REQUEST)
                body("message", equalTo("Bad Request"))
            } Extract {
                path("_embedded.errors")
            }

        assertThat(errors)
            .singleElement()
            .satisfies {
                assertThat(it).hasEntrySatisfying("message") { message ->
                    assertThat(message).contains("Missing required creator property 'datasetId'")
                }
            }
    }

    @MockBean(DatasetRecRunner::class)
    fun reconciliationService(): DatasetRecRunner {
        return service
    }

    @Replaces(H2RecRunRepository::class)
    @Singleton
    fun recRepository(): RecRunRepository {
        return runRepository
    }
}
