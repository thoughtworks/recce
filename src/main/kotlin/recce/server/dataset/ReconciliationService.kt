package recce.server.dataset

import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Inject
import jakarta.inject.Singleton
import mu.KotlinLogging
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2
import recce.server.config.DataLoadDefinition
import recce.server.config.DatasetConfiguration
import recce.server.config.ReconciliationConfiguration

private val logger = KotlinLogging.logger {}

interface ReconciliationRunner {
    fun runFor(datasetId: String): Mono<MigrationRun>
}

@Singleton
open class ReconciliationService(
    @Inject private val config: ReconciliationConfiguration,
    private val runService: MigrationRunService,
    private val recordRepository: MigrationRecordRepository
) : ReconciliationRunner {
    override fun runFor(datasetId: String): Mono<MigrationRun> {

        val datasetConfig = config.datasets[datasetId] ?: throw IllegalArgumentException("[$datasetId] not found!")

        logger.info { "Starting reconciliation run for [$datasetId]..." }

        val migrationRun = runService.start(datasetId)

        return loadFromSourceThenTarget(datasetConfig, migrationRun)
            .flatMap { runResults -> migrationRun.map { it.apply { results = runResults } } }
            .flatMap { runService.complete(it) }
    }

    private fun loadFromSourceThenTarget(datasetConfig: DatasetConfiguration, migrationRun: Mono<MigrationRun>) =
        loadFromSource(datasetConfig.source, migrationRun).count()
            .zipWhen(
                { loadFromTarget(datasetConfig.target, migrationRun).count() },
                { sourceCount, targetCount -> DatasetResults(sourceCount, targetCount) }
            )

    private fun loadFromSource(source: DataLoadDefinition, run: Mono<MigrationRun>): Flux<MigrationRecord> =
        source.runQuery()
            .flatMap { result -> result.map(HashedRow::fromRow) }
            .zipWith(run.repeat())
            .map { (row, run) ->
                MigrationRecord(
                    MigrationRecordKey(run.id!!, row.migrationKey),
                    sourceData = row.hashedValue
                )
            }
            .flatMap { record -> recordRepository.save(record) }

    private fun loadFromTarget(target: DataLoadDefinition, run: Mono<MigrationRun>): Flux<MigrationRecord> =
        target.runQuery()
            .flatMap { result -> result.map(HashedRow::fromRow) }
            .zipWith(run.repeat())
            .flatMap { (row, run) ->
                val key = MigrationRecordKey(run.id!!, row.migrationKey)
                recordRepository
                    .findById(key)
                    .flatMap { record -> recordRepository.update(record.apply { targetData = row.hashedValue }) }
                    .switchIfEmpty(recordRepository.save(MigrationRecord(key, targetData = row.hashedValue)))
            }

    fun runIgnoreFailure(datasetIds: List<String>): Flux<MigrationRun> = Flux.fromIterable(datasetIds)
        .filter { it.isNotEmpty() }
        .flatMap { runFor(it) }
        .onErrorContinue { err, it -> logger.warn(err) { "Start-up rec run failed for dataset [$it]." } }

    @Scheduled(initialDelay = "0s", fixedDelay = "1d")
    open fun scheduledStart() {
        runIgnoreFailure(config.triggerOnStart).subscribe()
    }
}
