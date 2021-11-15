package recce.server.recrun

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.kotlin.core.publisher.toFlux
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
        StepVerifier.create(recordRepository.countMatchedByIdRecRunId(1))
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

        StepVerifier.create(recordRepository.countMatchedByIdRecRunId(savedRecords.last().id.recRunId))
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
            var key = 0
            Flux.fromIterable(testRecordData)
                .flatMap { (source, target) ->
                    recordRepository.save(RecRecord(RecRecordKey(run.id!!, "${++key}"), source, target))
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

        StepVerifier.create(recordRepository.findByIdInList(savedRecords.map { it.id }))
            .expectNextCount(10)
            .verifyComplete();
    }
}
