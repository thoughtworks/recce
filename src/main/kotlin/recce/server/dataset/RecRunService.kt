package recce.server.dataset

import jakarta.inject.Singleton
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.time.Instant

private val logger = KotlinLogging.logger {}

@Singleton
open class RecRunService(private val runRepository: RecRunRepository) {
    fun start(datasetId: String): Mono<RecRun> = runRepository
        .save(RecRun(datasetId))
        .doOnNext { logger.info { "Starting reconciliation run for $it}..." } }
        .cache()

    fun complete(run: RecRun): Mono<RecRun> =
        runRepository.update(run.apply { completedTime = Instant.now() })
            .doOnNext { logger.info { "Run completed for $it" } }
}
