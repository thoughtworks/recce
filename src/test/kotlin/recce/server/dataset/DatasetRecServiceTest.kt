package recce.server.dataset

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import recce.server.config.DataLoadDefinition
import recce.server.config.DatasetConfiguration
import recce.server.config.ReconciliationConfiguration

internal class DatasetRecServiceTest {
    private val testDataset = "test-dataset"

    @Test
    fun `should throw on missing dataset`() {
        assertThatThrownBy { DatasetRecService(mock(), mock(), mock()).runFor(testDataset) }
            .isExactlyInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining(testDataset)
    }

    @Test
    fun `should work with empty datasets`() {
        val dataLoadDefinition = mock<DataLoadDefinition>() {
            on { runQuery() } doReturn Flux.empty()
        }
        val recRun = RecRun(testDataset)
        val runService = mock<RecRunService>() {
            on { start(testDataset) } doReturn Mono.just(recRun)
            on { complete(recRun) } doReturn Mono.just(recRun)
        }
        val service = DatasetRecService(
            ReconciliationConfiguration(
                mapOf(
                    testDataset to DatasetConfiguration(
                        dataLoadDefinition,
                        dataLoadDefinition
                    )
                )
            ),
            runService, mock()
        )
        StepVerifier.create(service.runFor(testDataset))
            .assertNext {
                assertThat(it.results).isEqualTo(RecRunResults(DatasetResults(), DatasetResults()))
            }
            .verifyComplete()
    }
}
