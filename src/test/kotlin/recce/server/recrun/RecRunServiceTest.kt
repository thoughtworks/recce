package recce.server.recrun

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.lang.IllegalArgumentException
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
    fun `should set successful run with match status`() {
        val expectedMatchStatus = MatchStatus(1, 1, 1, 1)
        val recordRepository = mock<RecRecordRepository> {
            on { countMatchedByKeyRecRunId(any()) } doReturn Mono.just(expectedMatchStatus)
        }

        val runRepository = mock<RecRunRepository> {
            on { update(any()) } doReturn Mono.just(
                startedRun.apply {
                    sourceMeta = DatasetMeta()
                    targetMeta = DatasetMeta()
                }
            )
        }

        StepVerifier.create(RecRunService(runRepository, recordRepository).successful(startedRun))
            .assertNext {
                assertThat(it.completedTime).isAfterOrEqualTo(it.createdTime)
                assertThat(it.status).isEqualTo(RunStatus.Successful)
                assertThat(it.summary).isEqualTo(expectedMatchStatus)
            }
            .verifyComplete()
    }

    @Test
    fun `should set failed run`() {
        val runRepository = mock<RecRunRepository> {
            on { update(any()) } doReturn Mono.just(startedRun)
        }

        StepVerifier.create(RecRunService(runRepository, mock()).failed(startedRun, IllegalArgumentException("failed run!")))
            .assertNext {
                assertThat(it.completedTime).isAfterOrEqualTo(it.createdTime)
                assertThat(it.status).isEqualTo(RunStatus.Failed)
                assertThat(it.summary).isNull()
            }
            .verifyComplete()
    }
}
