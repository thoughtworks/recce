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
        Assertions.assertThatThrownBy { DataSetService(mock()).start("test-dataset") }
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

    @Test
    fun `start can stream a source dataset`() {
        StepVerifier.create(service.start("test-dataset"))
            .assertNext {
                assertThat(it)
                    .isEqualTo(
                        HashedRow(
                            "sourcedatacount",
                            "b57448e19e0e383cdabaf669a4b85676bb7061e7f3720e57ea148a5735de957a"
                        )
                    )
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
