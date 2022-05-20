package recce.server.api

import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import io.restassured.specification.RequestSpecification
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import recce.server.auth.AuthConfiguration
import recce.server.dataset.*
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

val configProvider = mock<DatasetConfigProvider> {
    on { availableDataSets } doReturn setOf(
        DatasetConfiguration(
            DataLoadDefinition("source1", QueryConfig("")),
            DataLoadDefinition("target1", QueryConfig(""))
        ).apply { id = "two" },
        DatasetConfiguration(
            DataLoadDefinition("source2", QueryConfig("")),
            DataLoadDefinition("target2", QueryConfig("")),
            Schedule("0 0 0 ? * *")
        ).apply { id = "datasets" },
    )
}

internal class DatasetControllerTest {

    @Test
    fun `should retrieve empty dataset Ids`() {
        assertThat(DatasetController(mock()).getDatasets())
            .isEqualTo(emptyList<Any>())
    }

    @Test
    fun `should retrieve sorted datasets`() {
        val testCurrentTime = ZonedDateTime.of(2021, 12, 20, 13, 14, 15, 0, ZoneId.of("UTC"))

        mockStatic(ZonedDateTime::class.java, Answers.CALLS_REAL_METHODS).use { mockedTime ->
            mockedTime.`when`<Any> { ZonedDateTime.now() }.thenReturn(testCurrentTime)

            assertThat(DatasetController(configProvider).getDatasets())
                .isEqualTo(
                    listOf(
                        DatasetApiModel(
                            "datasets",
                            DatasourceApiModel("source2"),
                            DatasourceApiModel("target2"),
                            ScheduleApiModel(
                                "0 0 0 ? * *",
                                ZonedDateTime.of(2021, 12, 21, 0, 0, 0, 0, ZoneId.of("UTC"))
                            )
                        ),
                        DatasetApiModel(
                            "two",
                            DatasourceApiModel("source1"),
                            DatasourceApiModel("target1")
                        ),
                    )
                )
        }
    }
}

@MicronautTest
internal class DatasetControllerApiTest {

    @Inject
    lateinit var spec: RequestSpecification

    @Inject
    lateinit var authConfig: AuthConfiguration

    @MockBean(DatasetConfigProvider::class)
    fun mockConfigProvider(): DatasetConfigProvider {
        return configProvider
    }

    @Test
    fun `can get datasets`() {
        Given {
            spec(spec).auth().preemptive().basic(authConfig.username, authConfig.password)
        } When {
            get("/datasets")
        } Then {
            val expectedTriggerTime = ZonedDateTime.now().plusDays(1).truncatedTo(ChronoUnit.DAYS)
            body(".", hasSize<Any>(2))
            body("[0].id", equalTo("datasets"))
            body("[0].source.ref", equalTo("source2"))
            body("[0].target.ref", equalTo("target2"))
            body("[0].schedule.cronExpression", equalTo("0 0 0 ? * *"))
            body("[0].schedule.nextTriggerTime", equalTo(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(expectedTriggerTime)))
            body("[1].id", equalTo("two"))
            body("[1].source.ref", equalTo("source1"))
            body("[1].target.ref", equalTo("target1"))
        }
    }
}
