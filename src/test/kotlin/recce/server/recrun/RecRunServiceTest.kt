package recce.server.recrun

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant

internal class RecRunServiceTest {

    private val datasetId = "my-dataset"
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
        val expectedMatchStatus = MatchStatus(1, 1, 1, 1)
        val recordRepository = mock<RecRecordRepository> {
            on { countMatchedByIdRecRunId(any()) } doReturn Mono.just(expectedMatchStatus)
        }

        val runRepository = mock<RecRunRepository> {
            on { update(any()) } doReturn Mono.just(
                startedRun.apply {
                    sourceMeta = DatasetMeta()
                    targetMeta = DatasetMeta()
                }
            )
        }

        StepVerifier.create(RecRunService(runRepository, recordRepository).complete(startedRun))
            .assertNext {
                assertThat(it.completedTime).isAfterOrEqualTo(it.createdTime)
                assertThat(it.summary).isEqualTo(expectedMatchStatus)
            }
            .verifyComplete()
    }
}
