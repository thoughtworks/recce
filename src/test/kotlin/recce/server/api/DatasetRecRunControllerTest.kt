package recce.server.api

import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.restassured.builder.RequestSpecBuilder
import io.restassured.filter.log.ResponseLoggingFilter
import io.restassured.http.ContentType
import io.restassured.module.kotlin.extensions.Extract
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import io.restassured.specification.RequestSpecification
import jakarta.inject.Inject
import org.apache.http.HttpStatus
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import recce.server.dataset.DatasetRecRunner
import recce.server.dataset.DatasetRecService
import recce.server.dataset.RecRun
import recce.server.dataset.RecRunResults
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal class DatasetRecRunControllerTest {

    private val testDataset = "testDataset"

    private val service = mock<DatasetRecService> {
        on { runFor(eq(testDataset)) } doReturn Mono.just(RecRun(testDataset))
    }

    private val controller = DatasetRecRunController(service)

    @Test
    fun `controller should delegate to service`() {
        StepVerifier.create(controller.create(DatasetRecRunController.RunCreationParams(eq(testDataset))))
            .expectNext(RecRun(testDataset))
            .verifyComplete()
    }
}

@MicronautTest
internal class DatasetRecRunControllerApiTest {
    private val testDataset = "testDataset"
    private val testResults = RecRun(
        id = 12,
        datasetId = testDataset,
        createdTime = LocalDateTime.of(2021, 10, 25, 16, 16, 16).toInstant(ZoneOffset.UTC),
    ).apply {
        completedTime = createdTime?.plusSeconds(180)
        updatedTime = completedTime?.plusSeconds(10)
        results = RecRunResults(2000, 3000)
    }

    @Inject
    lateinit var server: EmbeddedServer
    lateinit var spec: RequestSpecification

    @BeforeEach
    fun setUp() {
        spec = RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setBaseUri(server.uri)
            .addFilter(ResponseLoggingFilter())
            .build()
    }

    @Test
    fun `controller should delegate to service`() {
        Given {
            spec(spec)
        } When {
            body(mapOf("datasetId" to testDataset))
            post("/runs")
        } Then {
            statusCode(HttpStatus.SC_OK)
            body("datasetId", equalTo(testDataset))
            body("id", equalTo(testResults.id))
            body("createdTime", equalTo(DateTimeFormatter.ISO_INSTANT.format(testResults.createdTime)))
            body("completedTime", equalTo(DateTimeFormatter.ISO_INSTANT.format(testResults.completedTime)))
            body("updatedTime", equalTo(DateTimeFormatter.ISO_INSTANT.format(testResults.updatedTime)))
            body("results.sourceRows", equalTo(testResults.results?.sourceRows?.toInt()))
            body("results.targetRows", equalTo(testResults.results?.targetRows?.toInt()))
        }
    }

    @Test
    fun `controller should validate params`() {
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
        return mock {
            on { runFor(eq(testDataset)) } doReturn Mono.just(testResults)
        }
    }
}
