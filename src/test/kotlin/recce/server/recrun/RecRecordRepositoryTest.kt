package recce.server.recrun

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.kotlin.core.publisher.toFlux
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2
import reactor.test.StepVerifier

@MicronautTest
class RecRecordRepositoryTest {
    @Inject
    lateinit var runRepository: RecRunRepository

    @Inject
    lateinit var recordRepository: RecRecordRepository

    @BeforeEach
    fun setup() {
        runRepository.deleteAll().block()
    }

    @Test
    fun `should count matches when empty`() {
        StepVerifier.create(recordRepository.countMatchedByKeyRecRunId(1))
            .expectNext(MatchStatus())
            .verifyComplete()
    }

    @Test
    fun `should count matches of various types`() {
        val testRecordData =
            List(1) { "test" to null } +
                List(2) { null to "test" } +
                List(3) { "test" to "test" } +
                List(4) { "test" to "test3" }

        val savedRecords = mutableListOf<RecRecord>()
        StepVerifier.create(saveTestRecords(testRecordData))
            .recordWith { savedRecords }
            .expectNextCount(testRecordData.size.toLong())
            .verifyComplete()

        StepVerifier.create(recordRepository.countMatchedByKeyRecRunId(savedRecords.last().key.recRunId))
            .assertNext {
                assertThat(it).isEqualTo(MatchStatus(1, 2, 3, 4))
                assertThat(it.sourceTotal).isEqualTo(8)
                assertThat(it.targetTotal).isEqualTo(9)
                assertThat(it.total).isEqualTo(10)
            }
            .verifyComplete()
    }

    private fun saveTestRecords(testRecordData: List<Pair<String?, String?>>): Flux<RecRecord> {
        return runRepository.save(RecRun("test-dataset")).toFlux().flatMap { run ->
            Flux.fromIterable(testRecordData)
                .index()
                .flatMap { (i, data) ->
                    recordRepository.save(RecRecord(key = RecRecordKey(run.id!!, "${i + 1}"), sourceData = data.first, targetData = data.second))
                }
        }
    }

    @Test
    fun `should bulk check for existence of records`() {
        val testRecordData = List(10) { "test" to null }
        val savedRecords = mutableListOf<RecRecord>()
        StepVerifier.create(saveTestRecords(testRecordData))
            .recordWith { savedRecords }
            .expectNextCount(testRecordData.size.toLong())
            .verifyComplete()

        val foundRecords = mutableListOf<RecRecord>()
        StepVerifier.create(recordRepository.findByRecRunIdAndMigrationKeyIn(savedRecords.first().recRunId, savedRecords.map { it.migrationKey }))
            .recordWith { foundRecords }
            .expectNextCount(testRecordData.size.toLong())
            .verifyComplete()

        assertThat(savedRecords).usingRecursiveComparison().ignoringCollectionOrder().isEqualTo(foundRecords)
    }
}
