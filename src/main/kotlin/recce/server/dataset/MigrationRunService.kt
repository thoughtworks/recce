package recce.server.dataset

import jakarta.inject.Singleton
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

@Singleton
open class MigrationRunService(private val runRepository: MigrationRunRepository) {
    fun start(datasetId: String): Mono<MigrationRun> = runRepository
        .save(MigrationRun(datasetId))
        .doOnNext { logger.info { "Starting reconciliation run for $it}..." } }
        .cache()

    fun complete(run: MigrationRun): Mono<MigrationRun> =
        runRepository.update(run.apply { completedTime = LocalDateTime.now() })
            .doOnNext { logger.info { "Run completed for $it" } }
}
