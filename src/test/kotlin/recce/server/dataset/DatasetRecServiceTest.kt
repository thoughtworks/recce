package recce.server.dataset

import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.kotlin.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.test.test
import recce.server.RecConfiguration
import recce.server.recrun.*
import java.util.function.BiFunction

internal class DatasetRecServiceTest {
    private val testDataset = "test-dataset"
    private val recRun = RecRun(1, testDataset)

    private val runService = mock<RecRunService> {
        on { start(eq(testDataset), any()) } doReturn Mono.just(recRun)
        on { successful(recRun) } doReturn Mono.just(recRun).map { recRun.asSuccessful(MatchStatus()) }
        on { failed(eq(recRun), any()) } doReturn Mono.just(recRun).map { recRun.asFailed(IllegalArgumentException()) }
    }

    private val emptyRowResult = mock<io.r2dbc.spi.Result> {
        on { map(any<BiFunction<Row, RowMetadata, HashedRow>>()) } doReturn Flux.empty()
    }

    private val emptyDataLoad = mock<DataLoadDefinition> {
        on { runQuery() } doReturn Flux.just(emptyRowResult)
    }

    private val testMeta = listOf(FakeColumnMetadata("col1", String::class.java))

    private val singleRowResult = mock<io.r2dbc.spi.Result> {
        on { map(any<BiFunction<Row, RowMetadata, HashedRow>>()) } doReturn Flux.just(HashedRow("abc", "def", testMeta))
    }

    private val singleRowDataLoad = mock<DataLoadDefinition> {
        on { runQuery() } doReturn Flux.just(singleRowResult)
    }

    private val testRecordKey = RecRecordKey(1, "abc")
    private val testRecord = RecRecord(key = testRecordKey)
    private val recordRepository = mock<RecRecordRepository> {
        on { save(any()) } doReturn Mono.just(testRecord)
        on { saveAll(anyList()) } doReturn Flux.just(testRecord)
        on { findByRecRunIdAndMigrationKeyIn(eq(testRecordKey.recRunId), anyList()) } doReturn Flux.just(testRecord)
        on { updateAll(anyList()) } doReturn Flux.just(testRecord)
    }

    @Test
    fun `should throw on missing dataset`() {
        assertThatThrownBy { DatasetRecService(mock(), mock(), mock(), mock(), mock()).runFor(testDataset) }
            .isExactlyInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining(testDataset)
    }

    @Test
    fun `mono should return failed run on failed data load`() {
        val service = DatasetRecService(
            RecConfiguration(mapOf(testDataset to DatasetConfiguration(emptyDataLoad, emptyDataLoad))),
            mock(),
            mock(),
            runService,
            mock()
        )

        val rootCause = IllegalArgumentException("Could not connect to database")
        whenever(emptyDataLoad.runQuery()).thenReturn(Flux.error(rootCause))

        service.runFor(testDataset)
            .test()
            .assertNext {
                assertThat(it.status).isEqualTo(RunStatus.Failed)
            }
            .verifyComplete()

        val errorCaptor = argumentCaptor<Throwable>()
        verify(runService).failed(eq(recRun), errorCaptor.capture())

        assertThat(errorCaptor.firstValue)
            .isInstanceOf(DataLoadException::class.java)
            .hasCause(rootCause)
            .hasMessageContaining("Failed to load data")
            .hasMessageContaining(rootCause.message)
    }

    @Test
    fun `mono should error on failed initial save`() {
        val service = DatasetRecService(
            RecConfiguration(mapOf(testDataset to DatasetConfiguration(emptyDataLoad, emptyDataLoad))),
            mock(),
            mock(),
            runService,
            mock()
        )

        whenever(runService.start(any(), any())).thenReturn(Mono.error(IllegalArgumentException("failed!")))

        service.runFor(testDataset)
            .test()
            .expectErrorSatisfies {
                assertThat(it)
                    .isExactlyInstanceOf(IllegalArgumentException::class.java)
                    .hasMessageContaining("failed!")
            }
            .verify()
    }

    @Test
    fun `mono should error on failed save of failed run`() {
        val service = DatasetRecService(
            RecConfiguration(mapOf(testDataset to DatasetConfiguration(emptyDataLoad, emptyDataLoad))),
            mock(),
            mock(),
            runService,
            mock()
        )

        val rootCause = IllegalArgumentException("Could not connect to database")
        whenever(emptyDataLoad.runQuery()).thenReturn(Flux.error(rootCause))

        val failSaveCause = IllegalArgumentException("Could not save failure status")
        whenever(runService.failed(any(), any())).thenReturn(Mono.error(failSaveCause))

        service.runFor(testDataset)
            .test()
            .expectErrorSatisfies {
                assertThat(it)
                    .isEqualTo(failSaveCause)
            }
            .verify()
    }

    @Test
    fun `should reconcile empty datasets without error`() {
        val service = DatasetRecService(
            RecConfiguration(mapOf(testDataset to DatasetConfiguration(emptyDataLoad, emptyDataLoad))),
            mock(),
            mock(),
            runService,
            mock()
        )
        service.runFor(testDataset)
            .test()
            .assertNext {
                assertThat(it.status).isEqualTo(RunStatus.Successful)
                assertThat(it.sourceMeta.cols).isEmpty()
                assertThat(it.targetMeta.cols).isEmpty()
            }
            .verifyComplete()

        verifyNoMoreInteractions(recordRepository)
    }

    @Test
    fun `should reconcile source with empty target`() {
        val service = DatasetRecService(
            RecConfiguration(mapOf(testDataset to DatasetConfiguration(singleRowDataLoad, emptyDataLoad))),
            mock(),
            mock(),
            runService,
            recordRepository
        )

        service.runFor(testDataset)
            .test()
            .assertNext {
                assertThat(it.status).isEqualTo(RunStatus.Successful)
                assertThat(it.sourceMeta.cols).isNotEmpty
                assertThat(it.targetMeta.cols).isEmpty()
            }
            .verifyComplete()

        verify(recordRepository).saveAll(listOf(RecRecord(key = testRecordKey, sourceData = "def")))
        verifyNoMoreInteractions(recordRepository)
        verify(runService).successful(recRun)
    }

    @Test
    fun `should reconcile empty source with target`() {
        whenever(
            recordRepository.findByRecRunIdAndMigrationKeyIn(
                testRecordKey.recRunId,
                listOf(testRecordKey.migrationKey)
            )
        ).doReturn(Flux.empty())

        val service = DatasetRecService(
            RecConfiguration(mapOf(testDataset to DatasetConfiguration(emptyDataLoad, singleRowDataLoad))),
            mock(),
            mock(),
            runService,
            recordRepository
        )

        service.runFor(testDataset)
            .test()
            .assertNext {
                assertThat(it.status).isEqualTo(RunStatus.Successful)
                assertThat(it.sourceMeta.cols).isEmpty()
                assertThat(it.targetMeta.cols).isNotEmpty
            }
            .verifyComplete()

        verify(recordRepository).findByRecRunIdAndMigrationKeyIn(
            testRecordKey.recRunId,
            listOf(testRecordKey.migrationKey)
        )
        verify(recordRepository).saveAll(listOf(RecRecord(key = testRecordKey, targetData = "def")))
        verifyNoMoreInteractions(recordRepository)
        verify(runService).successful(recRun)
    }

    @Test
    fun `should reconcile source with target with different rows`() {
        // yes, we are re-using the same key, but let's pretend they are different by telling
        // the code that the row doesn't exist
        whenever(
            recordRepository.findByRecRunIdAndMigrationKeyIn(
                testRecordKey.recRunId,
                listOf(testRecordKey.migrationKey)
            )
        ).doReturn(Flux.empty())

        val service = DatasetRecService(
            RecConfiguration(mapOf(testDataset to DatasetConfiguration(singleRowDataLoad, singleRowDataLoad))),
            mock(),
            mock(),
            runService,
            recordRepository
        )

        service.runFor(testDataset)
            .test()
            .assertNext {
                assertThat(it.status).isEqualTo(RunStatus.Successful)
                assertThat(it.sourceMeta.cols).isNotEmpty
                assertThat(it.targetMeta.cols).isNotEmpty
            }
            .verifyComplete()

        verify(recordRepository).saveAll(listOf(RecRecord(key = testRecordKey, sourceData = "def")))
        verify(recordRepository).findByRecRunIdAndMigrationKeyIn(
            testRecordKey.recRunId,
            listOf(testRecordKey.migrationKey)
        )
        verify(recordRepository).saveAll(listOf(RecRecord(key = testRecordKey, targetData = "def")))
        verifyNoMoreInteractions(recordRepository)
        verify(runService).successful(recRun)
    }

    @Test
    fun `should reconcile source with target when rows have matching key`() {
        whenever(
            recordRepository.findByRecRunIdAndMigrationKeyIn(
                testRecordKey.recRunId,
                listOf(testRecordKey.migrationKey)
            )
        ).doReturn(Flux.just(testRecord))

        val service = DatasetRecService(
            RecConfiguration(mapOf(testDataset to DatasetConfiguration(singleRowDataLoad, singleRowDataLoad))),
            mock(),
            mock(),
            runService,
            recordRepository
        )

        service.runFor(testDataset)
            .test()
            .assertNext {
                assertThat(it.status).isEqualTo(RunStatus.Successful)
                assertThat(it.sourceMeta.cols).isNotEmpty
                assertThat(it.targetMeta.cols).isNotEmpty
            }
            .verifyComplete()

        verify(recordRepository).saveAll(listOf(RecRecord(key = testRecordKey, sourceData = "def")))
        verify(recordRepository).findByRecRunIdAndMigrationKeyIn(
            testRecordKey.recRunId,
            listOf(testRecordKey.migrationKey)
        )
        verify(recordRepository).updateAll(listOf(testRecord))
        verifyNoMoreInteractions(recordRepository)
        verify(runService).successful(recRun)
    }

    @Test
    fun `should be able to retrieve available datasets`() {
        val first = mock<DatasetConfiguration>()
        val second = mock<DatasetConfiguration>()
        val datasetConfig = mapOf("a" to first, "b" to second)
        assertThat(DatasetRecService(RecConfiguration(datasetConfig), mock(), mock(), mock(), mock()).availableDataSets)
            .hasSameElementsAs(listOf(first, second))
    }
}
