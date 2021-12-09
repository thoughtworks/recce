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
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import recce.server.dataset.DatasetConfigProvider

val configProvider = mock<DatasetConfigProvider> {
    on { availableDataSetIds } doReturn setOf("lots", "of", "datasets")
}

internal class DatasetControllerTest {

    @Test
    fun `should retrieve empty dataset Ids`() {
        assertThat(DatasetController(mock()).getDatasets())
            .isEqualTo(emptyList<String>())
    }

    @Test
    fun `should retrieve sorted dataset Ids`() {
        assertThat(DatasetController(configProvider).getDatasets())
            .isEqualTo(listOf("datasets", "lots", "of"))
    }
}

@MicronautTest
internal class DatasetControllerApiTest {

    @Inject
    lateinit var spec: RequestSpecification

    @MockBean(DatasetConfigProvider::class)
    fun mockConfigProvider(): DatasetConfigProvider {
        return configProvider
    }

    @Test
    fun `can get dataset IDs`() {
        Given {
            spec(spec)
        } When {
            get("/datasets")
        } Then {
            body(".", equalTo(listOf("datasets", "lots", "of")))
        }
    }
}
