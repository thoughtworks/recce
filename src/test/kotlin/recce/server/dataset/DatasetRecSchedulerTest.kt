package recce.server.dataset

import io.micronaut.context.ApplicationContext
import io.micronaut.scheduling.TaskScheduler
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import recce.server.RecConfiguration
import recce.server.api.DatasetRecRunController
import recce.server.recrun.RecRun
import java.util.concurrent.TimeUnit

internal class DatasetRecSchedulerTest {
    private val testDataset = "test-dataset"

    private val runner = mock<DatasetRecRunner> {
        on { runFor(testDataset) } doReturn Mono.just(RecRun(testDataset))
    }

    private val scheduler = mock<TaskScheduler>()

    @Test
    fun `does nothing when there is nothing to schedule`() {
        DatasetRecScheduler(mock(), runner, scheduler).onApplicationEvent(null)

        verifyNoInteractions(scheduler)
    }

    @Test
    fun `schedules a single run`() {
        val cronExpression = "0 0 * * *"
        val datasetConfig = mock<DatasetConfiguration> {
            on { name } doReturn testDataset
            on { schedule } doReturn Schedule(cronExpression)
        }

        val config = RecConfiguration(mapOf(testDataset to datasetConfig))

        DatasetRecScheduler(config, runner, scheduler).onApplicationEvent(null)

        val workCaptor = argumentCaptor<Runnable>()
        verify(scheduler).schedule(eq(cronExpression), workCaptor.capture())

        workCaptor.firstValue.run()

        verify(runner).runFor(testDataset)
    }
}

internal class DatasetRecSchedulerIntegrationTest {

    private lateinit var ctx: ApplicationContext

    private val everySecond = "* * * * * *"

    @BeforeEach
    fun setup() {
        ctx = ApplicationContext.run(
            mapOf(
                "reconciliation.datasets.test-dataset.schedule.cronExpression" to everySecond,
                "reconciliation.datasets.test-dataset.source.dataSourceRef" to "default",
                "reconciliation.datasets.test-dataset.source.query" to "select id as MigrationKey from reconciliation_run",
                "reconciliation.datasets.test-dataset.target.dataSourceRef" to "default",
                "reconciliation.datasets.test-dataset.target.query" to "select id as MigrationKey from reconciliation_run"
            )
        )
    }

    @AfterEach
    fun tearDown() {
        ctx.stop()
    }

    @Test
    fun `can run a scheduled reconciliation`() {
        ctx.getBean(DatasetRecScheduler::class.java).onApplicationEvent(null)

        await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            StepVerifier.create(ctx.getBean(DatasetRecRunController::class.java).get("test-dataset").collectList())
                .assertNext { assertThat(it).hasSize(1) }
                .verifyComplete()
        }
    }
}
