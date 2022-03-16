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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.test.test
import recce.server.dataset.DataLoadException
import recce.server.dataset.DatasetRecRunner
import recce.server.recrun.*
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val sampleKeysLimit = 3
private const val notFoundId = 0
private const val testDataset = "testDataset"
private val testCompletedDuration = Duration.ofMinutes(3).plusNanos(234)
private val testResults = RecRun(
    id = 12,
    datasetId = testDataset,
    createdTime = LocalDateTime.of(2021, 10, 25, 16, 16, 16).toInstant(ZoneOffset.UTC),
).apply {
    completedTime = createdTime?.plusNanos(testCompletedDuration.toNanos())
    status = RunStatus.Successful
    updatedTime = completedTime?.plusSeconds(10)
    sourceMeta = DatasetMeta(listOf(recce.server.recrun.ColMeta("test1", "String")))
    targetMeta = DatasetMeta(listOf(recce.server.recrun.ColMeta("test1", "String")))
    summary = MatchStatus(1, 2, 3, 4)
    metadata = mapOf("sourceQuery" to "mockQuery", "targetQuery" to "mockQuery")
}

private fun mockService() = mock<DatasetRecRunner> {
    on { runFor(eq(testDataset)) } doReturn Mono.just(testResults)
}

private fun mockRunRepository() = mock<RecRunRepository> {
    on { findById(testResults.id!!) } doReturn Mono.just(testResults)
    on { existsById(testResults.id!!) } doReturn Mono.just(true)
    on { findById(notFoundId) } doReturn Mono.empty()
    on { existsById(notFoundId) } doReturn Mono.just(false)
    on { findTop10ByDatasetIdOrderByCompletedTimeDesc(testDataset) } doReturn Flux.just(testResults, testResults)
}

private fun mockRecordRepository(sampleRecords: List<RecRecord>) = mock<RecRecordRepository> {
    on { findFirstByRecRunIdSplitByMatchStatus(testResults.id!!, sampleKeysLimit) } doReturn Flux.fromIterable(sampleRecords)
    on { findFirstByRecRunIdSplitByMatchStatus(notFoundId, sampleKeysLimit) } doReturn Flux.empty()
}

internal class DatasetRecRunControllerTest {
    private val sampleRows =
        List(1) { RecRecord(RecRecordKey(testResults.id!!, "source-$it"), sourceData = "set") } +
            List(2) { RecRecord(RecRecordKey(testResults.id!!, "target-$it"), targetData = "set") } +
            List(3) { RecRecord(RecRecordKey(testResults.id!!, "both-$it"), sourceData = "set", targetData = "set2") }

    private val service = mockService()
    private val runRepository = mockRunRepository()
    private val controller = DatasetRecRunController(service, runRepository, mockRecordRepository(sampleRows))

    @Test
    fun `can get run by id`() {
        controller.retrieveIndividualRun(DatasetRecRunController.IndividualRunQueryParams(testResults.id!!))
            .test()
            .assertNext {
                assertThatModelMatchesTestResults(it)
                assertThat(it.summary?.source?.onlyHereSampleKeys).isNull()
                assertThat(it.summary?.target?.onlyHereSampleKeys).isNull()
                assertThat(it.summary?.bothMismatchedSampleKeys).isNull()
            }
            .verifyComplete()
    }

    @Test
    fun `can get run by id with limited sample bad rows`() {
        controller.retrieveIndividualRun(DatasetRecRunController.IndividualRunQueryParams(testResults.id!!, sampleKeysLimit))
            .test()
            .assertNext {
                assertThatModelMatchesTestResults(it)
                assertThat(it.summary?.source?.onlyHereSampleKeys).containsExactly("source-0")
                assertThat(it.summary?.target?.onlyHereSampleKeys).containsExactly("target-0", "target-1")
                assertThat(it.summary?.bothMismatchedSampleKeys).containsExactly("both-0", "both-1", "both-2")
            }
            .verifyComplete()
    }

    private fun assertThatModelMatchesTestResults(apiModel: RunApiModel) {
        SoftAssertions.assertSoftly { softly ->
            softly.assertThat(apiModel.id).isEqualTo(testResults.id)
            softly.assertThat(apiModel.datasetId).isEqualTo(testResults.datasetId)
            softly.assertThat(apiModel.createdTime).isEqualTo(testResults.createdTime)
            softly.assertThat(apiModel.completedTime).isEqualTo(testResults.completedTime)
            softly.assertThat(apiModel.status).isEqualTo(testResults.status)
            softly.assertThat(apiModel.failureCause).isNull()
            softly.assertThat(apiModel.summary?.totalCount).isEqualTo(testResults.summary?.total)
            softly.assertThat(apiModel.summary?.bothMatchedCount).isEqualTo(testResults.summary?.bothMatched)
            softly.assertThat(apiModel.summary?.bothMismatchedCount).isEqualTo(testResults.summary?.bothMismatched)
            softly.assertThat(apiModel.summary?.source?.totalCount).isEqualTo(testResults.summary?.sourceTotal)
            softly.assertThat(apiModel.summary?.source?.onlyHereCount).isEqualTo(testResults.summary?.sourceOnly)
            softly.assertThat(apiModel.summary?.source?.meta).usingRecursiveComparison().isEqualTo(testResults.sourceMeta)
            softly.assertThat(apiModel.summary?.target?.totalCount).isEqualTo(testResults.summary?.targetTotal)
            softly.assertThat(apiModel.summary?.target?.onlyHereCount).isEqualTo(testResults.summary?.targetOnly)
            softly.assertThat(apiModel.summary?.target?.meta).usingRecursiveComparison().isEqualTo(testResults.targetMeta)
            softly.assertThat(apiModel.metadata).usingRecursiveComparison().isEqualTo(testResults.metadata)
        }
    }

    @Test
    fun `can get runs by dataset id`() {
        controller.retrieveRuns(DatasetRecRunController.RunQueryParams(testDataset))
            .test()
            .assertNext(::assertThatModelMatchesTestResults)
            .assertNext(::assertThatModelMatchesTestResults)
            .verifyComplete()
    }

    @Test
    fun `trigger should delegate to service`() {
        controller.triggerRun(DatasetRecRunController.RunCreationParams(testDataset))
            .test()
            .assertNext(::assertThatModelMatchesTestResults)
            .verifyComplete()
    }

    @Test
    fun `failed run should return with cause`() {
        val failureCause = DataLoadException("Could not load data", IllegalArgumentException("Root Cause"))
        val failedRun = RecRun(
            id = testResults.id,
            datasetId = testDataset,
            createdTime = testResults.createdTime,
        ).asFailed(failureCause)

        whenever(service.runFor(testDataset)).doReturn(Mono.just(failedRun))

        controller.triggerRun(DatasetRecRunController.RunCreationParams(testDataset))
            .test()
            .assertNext { apiModel ->
                SoftAssertions.assertSoftly { softly ->
                    softly.assertThat(apiModel.id).isEqualTo(testResults.id)
                    softly.assertThat(apiModel.datasetId).isEqualTo(testResults.datasetId)
                    softly.assertThat(apiModel.createdTime).isEqualTo(testResults.createdTime)
                    softly.assertThat(apiModel.completedTime).isNotNull
                    softly.assertThat(apiModel.status).isEqualTo(RunStatus.Failed)
                    softly.assertThat(apiModel.failureCause).isEqualTo("Could not load data, rootCause=[Root Cause]")
                }
            }.verifyComplete()
    }
}

@MicronautTest
internal class DatasetRecRunControllerApiTest {

    val sampleRows =
        List(1) { RecRecord(RecRecordKey(testResults.id!!, "source-$it"), sourceData = "set") } +
            List(1) { RecRecord(RecRecordKey(testResults.id!!, "target-$it"), targetData = "set") } +
            List(1) { RecRecord(RecRecordKey(testResults.id!!, "both-$it"), sourceData = "set", targetData = "set2") }

    @Inject
    lateinit var spec: RequestSpecification

    @Test
    fun `can get run by id`() {
        Given {
            spec(spec)
        } When {
            get("/runs/${testResults.id!!}?includeSampleKeys=$sampleKeysLimit")
        } Then {
            validateTestResultsAreReturned(expectSampleKeys = true)
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

    @ParameterizedTest
    @ValueSource(ints = [-1, 200])
    fun `get 400 on run by id when bad sample count supplied`(count: Int) {
        Given {
            spec(spec)
        } When {
            get("/runs/${testResults.id!!}?includeSampleKeys=$count")
        } Then {
            statusCode(HttpStatus.SC_BAD_REQUEST)
            body("message", equalTo("Bad Request"))
            body("_embedded.errors[0].message", startsWith("params.includeSampleKeys: must be"))
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

    private fun ValidatableResponse.validateTestResultsAreReturned(expectSampleKeys: Boolean = false) {
        statusCode(HttpStatus.SC_OK)
        body("datasetId", equalTo(testDataset))
        body("id", equalTo(testResults.id))
        body("createdTime", equalTo(DateTimeFormatter.ISO_INSTANT.format(testResults.createdTime)))
        body("completedTime", equalTo(DateTimeFormatter.ISO_INSTANT.format(testResults.completedTime)))
        body("completedDurationSeconds", closeTo(testCompletedDuration.toSeconds().toDouble(), 0.00001))
        body("status", equalTo("Successful"))
        body(
            "summary",
            allOf(
                hasEntry("totalCount", 10),
                hasEntry("bothMatchedCount", 3),
                hasEntry("bothMismatchedCount", 4),
                if (expectSampleKeys) hasEntry("bothMismatchedSampleKeys", listOf("both-0")) else not(hasKey("bothMismatchedSampleKeys"))
            )
        )
        body(
            "summary.source",
            allOf(
                hasEntry("totalCount", 8),
                hasEntry("onlyHereCount", 1),
                if (expectSampleKeys) hasEntry("onlyHereSampleKeys", listOf("source-0")) else not(hasKey("onlyHereSampleKeys"))
            )
        )
        body("summary.source.meta.cols", equalTo(listOf(mapOf("name" to "test1", "javaType" to "String"))))
        body(
            "summary.target",
            allOf(
                hasEntry("totalCount", 9),
                hasEntry("onlyHereCount", 2),
                if (expectSampleKeys) hasEntry("onlyHereSampleKeys", listOf("target-0")) else not(hasKey("onlyHereSampleKeys"))
            )
        )
        body("summary.target.meta.cols", equalTo(listOf(mapOf("name" to "test1", "javaType" to "String"))))
        body("metadata", equalTo(mapOf("sourceQuery" to "mockQuery", "targetQuery" to "mockQuery")))
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
        return mockService()
    }

    @Replaces(H2RecRunRepository::class)
    @Singleton
    fun runRepository(): RecRunRepository {
        return mockRunRepository()
    }

    @Replaces(H2RecRecordRepository::class)
    @Singleton
    fun recordRepository(): RecRecordRepository {
        return mockRecordRepository(sampleRows)
    }
}
