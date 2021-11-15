package recce.server.recrun

import jakarta.inject.Singleton
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.time.Instant

private val logger = KotlinLogging.logger {}

@Singleton
open class RecRunService(
    private val runRepository: RecRunRepository,
    private val recordRepository: RecRecordRepository
) {
    fun start(datasetId: String): Mono<RecRun> = runRepository
        .save(RecRun(datasetId))
        .doOnNext { logger.info { "Starting reconciliation run for $it}..." } }
        .cache()

    fun complete(run: RecRun): Mono<RecRun> {
        logger.info { "Summarising results for $run" }
        return recordRepository.countMatchedByKeyRecRunId(run.id!!)
            .map { run.apply { completedTime = Instant.now(); summary = it } }
            .flatMap(runRepository::update)
            .doOnNext { logger.info { "Run completed for $it" } }
    }
}
