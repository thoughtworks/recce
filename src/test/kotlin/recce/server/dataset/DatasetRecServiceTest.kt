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
import reactor.test.StepVerifier
import recce.server.RecConfiguration
import recce.server.recrun.*
import java.util.function.BiFunction

internal class DatasetRecServiceTest {
    private val testDataset = "test-dataset"
    private val recRun = RecRun(1, testDataset)

    private val runService = mock<RecRunService> {
        on { start(testDataset) } doReturn Mono.just(recRun)
        on { complete(recRun) } doReturn Mono.just(recRun)
    }

    private val emptyDataLoad = mock<DataLoadDefinition> {
        on { runQuery() } doReturn Flux.empty()
    }

    private val mockMeta = mock<RowMetadata> {
        on { columnMetadatas } doReturn listOf(TestR2dbcColumnMetadata("col1", String::class.java))
    }

    private val singleRowResult = mock<io.r2dbc.spi.Result> {
        on { map(any<BiFunction<Row, RowMetadata, HashedRow>>()) } doReturn Flux.just(HashedRow("abc", "def", mockMeta))
    }

    private val singleRowDataLoad = mock<DataLoadDefinition> {
        on { runQuery() } doReturn Flux.just(singleRowResult)
    }

    private val testRecordKey = RecRecordKey(1, "abc")
    private val testRecord = RecRecord(key = testRecordKey)
    private val recordRepository = mock<RecRecordRepository> {
        on { save(any()) } doReturn Mono.just(testRecord)
        on { saveAll(anyList()) } doReturn Flux.just(testRecord)
        on { updateByRecRunIdAndMigrationKey(eq(testRecordKey.recRunId), eq(testRecordKey.migrationKey), any()) } doReturn Mono.empty()
    }

    @Test
    fun `should throw on missing dataset`() {
        assertThatThrownBy { DatasetRecService(mock(), mock(), mock()).runFor(testDataset) }
            .isExactlyInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining(testDataset)
    }

    @Test
    fun `should reconcile empty datasets without error`() {
        val service = DatasetRecService(
            RecConfiguration(mapOf(testDataset to DatasetConfiguration(emptyDataLoad, emptyDataLoad))),
            runService,
            mock()
        )
        StepVerifier.create(service.runFor(testDataset))
            .assertNext {
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
            runService,
            recordRepository
        )

        StepVerifier.create(service.runFor(testDataset))
            .assertNext {
                assertThat(it.sourceMeta.cols).isNotEmpty
                assertThat(it.targetMeta.cols).isEmpty()
            }
            .verifyComplete()

        verify(recordRepository).saveAll(listOf(RecRecord(key = testRecordKey, sourceData = "def")))
        verifyNoMoreInteractions(recordRepository)
        verify(runService).complete(recRun)
    }

    @Test
    fun `should reconcile empty source with target`() {
        whenever(recordRepository.existsByRecRunIdAndMigrationKey(testRecordKey.recRunId, testRecordKey.migrationKey))
            .doReturn(Mono.just(false))

        val service = DatasetRecService(
            RecConfiguration(mapOf(testDataset to DatasetConfiguration(emptyDataLoad, singleRowDataLoad))),
            runService,
            recordRepository
        )

        StepVerifier.create(service.runFor(testDataset))
            .assertNext {
                assertThat(it.sourceMeta.cols).isEmpty()
                assertThat(it.targetMeta.cols).isNotEmpty
            }
            .verifyComplete()

        verify(recordRepository).existsByRecRunIdAndMigrationKey(testRecordKey.recRunId, testRecordKey.migrationKey)
        verify(recordRepository).save(RecRecord(key = testRecordKey, targetData = "def"))
        verifyNoMoreInteractions(recordRepository)
        verify(runService).complete(recRun)
    }

    @Test
    fun `should reconcile source with target with different rows row`() {
        // yes, we are re-using the same key, but let's pretend they are different by telling
        // the code that the row doesn't exist
        whenever(recordRepository.existsByRecRunIdAndMigrationKey(testRecordKey.recRunId, testRecordKey.migrationKey))
            .doReturn(Mono.just(false))

        val service = DatasetRecService(
            RecConfiguration(mapOf(testDataset to DatasetConfiguration(singleRowDataLoad, singleRowDataLoad))),
            runService,
            recordRepository
        )

        StepVerifier.create(service.runFor(testDataset))
            .assertNext {
                assertThat(it.sourceMeta.cols).isNotEmpty
                assertThat(it.targetMeta.cols).isNotEmpty
            }
            .verifyComplete()

        verify(recordRepository).saveAll(listOf(RecRecord(key = testRecordKey, sourceData = "def")))
        verify(recordRepository).existsByRecRunIdAndMigrationKey(testRecordKey.recRunId, testRecordKey.migrationKey)
        verify(recordRepository).save(RecRecord(key = testRecordKey, targetData = "def"))
        verifyNoMoreInteractions(recordRepository)
        verify(runService).complete(recRun)
    }

    @Test
    fun `should reconcile source with target when rows have matching key`() {
        whenever(recordRepository.existsByRecRunIdAndMigrationKey(testRecordKey.recRunId, testRecordKey.migrationKey))
            .doReturn(Mono.just(true))

        val service = DatasetRecService(
            RecConfiguration(mapOf(testDataset to DatasetConfiguration(singleRowDataLoad, singleRowDataLoad))),
            runService,
            recordRepository
        )

        StepVerifier.create(service.runFor(testDataset))
            .assertNext {
                assertThat(it.sourceMeta.cols).isNotEmpty
                assertThat(it.targetMeta.cols).isNotEmpty
            }
            .verifyComplete()

        verify(recordRepository).saveAll(listOf(RecRecord(key = testRecordKey, sourceData = "def")))
        verify(recordRepository).existsByRecRunIdAndMigrationKey(testRecordKey.recRunId, testRecordKey.migrationKey)
        verify(recordRepository).updateByRecRunIdAndMigrationKey(testRecordKey.recRunId, testRecordKey.migrationKey, targetData = "def")
        verifyNoMoreInteractions(recordRepository)
        verify(runService).complete(recRun)
    }
}
