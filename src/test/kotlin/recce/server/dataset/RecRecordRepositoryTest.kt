package recce.server.dataset

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
    fun tearDown() {
        runRepository.deleteAll().block()
    }

    @Test
    fun `should count matches when empty`() {
        StepVerifier.create(recordRepository.countMatchedByIdRecRunId(1))
            .expectNext(RecRecordRepository.MatchStatus())
            .verifyComplete()
    }

    @Test
    fun `should count matches of various types`() {
        val testRecordData =
            List(1) { "test" to null } +
                List(2) { null to "test" } +
                List(3) { "test" to "test" } +
                List(4) { "test" to "test3" }

        val setup = runRepository.save(RecRun("test-dataset")).toFlux().flatMap { run ->
            var key = 0
            Flux.fromIterable(testRecordData)
                .flatMap { (source, target) ->
                    recordRepository.save(RecRecord(RecRecordKey(run.id!!, "${++key}"), source, target))
                }
        }

        StepVerifier.create(setup)
            .expectNextCount(testRecordData.size.toLong())
            .verifyComplete()

        StepVerifier.create(recordRepository.countMatchedByIdRecRunId(1))
            .assertNext {
                assertThat(it).isEqualTo(RecRecordRepository.MatchStatus(1, 2, 3, 4))
                assertThat(it.sourceTotal).isEqualTo(8)
                assertThat(it.targetTotal).isEqualTo(9)
                assertThat(it.total).isEqualTo(10)
            }
            .verifyComplete()
    }
}
