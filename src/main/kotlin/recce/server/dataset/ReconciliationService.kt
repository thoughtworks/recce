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
import recce.server.config.DataSetConfiguration
import recce.server.config.ReconciliationConfiguration

private val logger = KotlinLogging.logger {}

@Singleton
open class ReconciliationService(
    @Inject private val config: ReconciliationConfiguration,
    private val runService: MigrationRunService,
    private val recordRepository: MigrationRecordRepository
) {
    fun runFor(dataSetId: String): Mono<MigrationRun> {

        val dataSetConfig = config.datasets[dataSetId] ?: throw IllegalArgumentException("[$dataSetId] not found!")

        logger.info { "Starting reconciliation run for [$dataSetId]..." }

        val migrationRun = runService.start(dataSetId)

        return loadFromSourceThenTarget(dataSetConfig, migrationRun)
            .flatMap { runResults -> migrationRun.map { it.apply { results = runResults } } }
            .flatMap { runService.complete(it) }
    }

    private fun loadFromSourceThenTarget(dataSetConfig: DataSetConfiguration, migrationRun: Mono<MigrationRun>) =
        loadFromSource(dataSetConfig.source, migrationRun).count()
            .zipWhen(
                { loadFromTarget(dataSetConfig.target, migrationRun).count() },
                { sourceCount, targetCount -> DataSetResults(sourceCount, targetCount) }
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

    fun runIgnoreFailure(dataSetIds: List<String>): Flux<MigrationRun> = Flux.fromIterable(dataSetIds)
        .filter { it.isNotEmpty() }
        .flatMap { runFor(it) }
        .onErrorContinue { err, it -> logger.warn(err) { "Start-up rec run failed for dataset [$it]." } }

    @Scheduled(initialDelay = "0s", fixedDelay = "1d")
    open fun scheduledStart() {
        runIgnoreFailure(config.triggerOnStart).subscribe()
    }
}
