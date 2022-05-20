package recce.server.dataset

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.scheduling.TaskScheduler
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import reactor.core.publisher.Mono
import reactor.kotlin.test.test
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
            on { id } doReturn testDataset
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

    private val everySecond = "* * * * * *"
    private val items = mutableMapOf<String, Any>(
        "reconciliation.datasets.test-dataset.schedule.cronExpression" to everySecond,
        "reconciliation.datasets.test-dataset.source.datasourceRef" to "default",
        "reconciliation.datasets.test-dataset.source.queryConfig.query" to "SELECT id AS MigrationKey FROM reconciliation_run",
        "reconciliation.datasets.test-dataset.target.datasourceRef" to "default",
        "reconciliation.datasets.test-dataset.target.queryConfig.query" to "SELECT id AS MigrationKey FROM reconciliation_run"
    )

    private lateinit var ctx: ApplicationContext

    @AfterEach
    fun tearDown() {
        ctx.stop()
    }

    @Test
    fun `can run a scheduled reconciliation`() {
        runScheduler().onApplicationEvent(null)

        await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            ctx.getBean(DatasetRecRunController::class.java).retrieveRuns(DatasetRecRunController.RunQueryParams("test-dataset")).collectList()
                .test()
                .assertNext { assertThat(it).hasSizeGreaterThanOrEqualTo(1) }
                .verifyComplete()
        }
    }

    @Test
    fun `fails startup on incorrect cron expression`() {
        items["reconciliation.datasets.test-dataset.schedule.cronExpression"] = "bad-cron"

        assertThatThrownBy { runScheduler().onApplicationEvent(null) }
            .isExactlyInstanceOf(ConfigurationException::class.java)
            .hasMessageContaining("Schedule for [test-dataset] is invalid")
            .hasMessageContaining("Invalid cron expression [bad-cron]")
            .hasCauseExactlyInstanceOf(IllegalArgumentException::class.java)
    }

    private fun runScheduler(): DatasetRecScheduler {
        ctx = ApplicationContext.run(items)
        return ctx.getBean(DatasetRecScheduler::class.java)
    }
}
