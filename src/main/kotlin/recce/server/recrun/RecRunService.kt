package recce.server.recrun

import jakarta.inject.Singleton
import mu.KotlinLogging
import reactor.core.publisher.Mono

private val logger = KotlinLogging.logger {}

@Singleton
open class RecRunService(
    private val runRepository: RecRunRepository,
    private val recordRepository: RecRecordRepository
) {
    fun start(
        datasetId: String,
        metadata: Map<String, String>
    ): Mono<RecRun> =
        runRepository
            .save(RecRun(datasetId).apply { this.metadata = metadata })
            .doOnNext { logger.info { "Starting reconciliation run for $it}..." } }
            .cache()

    fun successful(run: RecRun): Mono<RecRun> {
        logger.info { "Summarising results for $run" }
        return recordRepository.countMatchedByKeyRecRunId(run.id!!)
            .map { run.asSuccessful(it) }
            .flatMap(runRepository::update)
            .doOnNext { logger.info { "Run completed for $it" } }
    }

    fun failed(
        run: RecRun,
        cause: Throwable
    ): Mono<RecRun> {
        logger.info(cause) { "Recording failure for $run" }
        return runRepository.update(run.asFailed(cause))
    }
}
