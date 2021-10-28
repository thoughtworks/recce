package recce.server.dataset

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant

internal class RecRunServiceTest {

    private val startedRun = RecRun(1, datasetId, Instant.now())

    @Test
    fun `should return start results`() {

        val runRepository = mock<RecRunRepository> {
            on { save(any()) } doReturn Mono.just(startedRun)
        }

        val eventualRun = RecRunService(runRepository, mock()).start(datasetId)

        StepVerifier.create(eventualRun)
            .expectNext(startedRun)
            .verifyComplete()
    }

    @Test
    fun `complete should set completed time`() {
        val expectedMatchStatus = RecRecordRepository.MatchStatus(1, 1, 1, 1)
        val recordRepository = mock<RecRecordRepository>() {
            on { countMatchedByIdRecRunId(any()) } doReturn Mono.just(expectedMatchStatus)
        }

        val runRepository = mock<RecRunRepository> {
            on { update(any()) } doReturn Mono.just(startedRun.apply {
                results = RecRunResults(DatasetResults(), DatasetResults())
            })
        }

        StepVerifier.create(RecRunService(runRepository, recordRepository).complete(startedRun))
            .assertNext {
                assertThat(it.completedTime).isAfterOrEqualTo(it.createdTime)
                assertThat(it.results?.summary).isEqualTo(expectedMatchStatus)
            }
            .verifyComplete()
    }

    companion object {
        private const val datasetId = "my-dataset"
    }
}
