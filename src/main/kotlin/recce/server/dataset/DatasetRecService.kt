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

interface DatasetRecRunner {
    fun runFor(datasetId: String): Mono<RecRun>
}

@Singleton
open class DatasetRecService(
    @Inject private val config: ReconciliationConfiguration,
    private val runService: RecRunService,
    private val recordRepository: RecRecordRepository
) : DatasetRecRunner {
    override fun runFor(datasetId: String): Mono<RecRun> {

        val datasetConfig = config.datasets[datasetId] ?: throw IllegalArgumentException("[$datasetId] not found!")

        logger.info { "Starting reconciliation run for [$datasetId]..." }

        val recRun = runService.start(datasetId)

        return loadFromSourceThenTarget(datasetConfig, recRun)
            .flatMap { runResults -> recRun.map { it.apply { results = runResults } } }
            .flatMap { runService.complete(it) }
    }

    private fun loadFromSourceThenTarget(datasetConfig: DatasetConfiguration, recRun: Mono<RecRun>) =
        loadFromSource(datasetConfig.source, recRun).count()
            .zipWhen(
                { loadFromTarget(datasetConfig.target, recRun).count() },
                { sourceCount, targetCount -> RecRunResults(sourceCount, targetCount) }
            )

    private fun loadFromSource(source: DataLoadDefinition, run: Mono<RecRun>): Flux<RecRecord> =
        source.runQuery()
            .flatMap { result -> result.map(HashedRow::fromRow) }
            .zipWith(run.repeat())
            .map { (row, run) ->
                RecRecord(
                    RecRecordKey(run.id!!, row.migrationKey),
                    sourceData = row.hashedValue
                )
            }
            .flatMap { record -> recordRepository.save(record) }

    private fun loadFromTarget(target: DataLoadDefinition, run: Mono<RecRun>): Flux<RecRecord> =
        target.runQuery()
            .flatMap { result -> result.map(HashedRow::fromRow) }
            .zipWith(run.repeat())
            .flatMap { (row, run) ->
                val key = RecRecordKey(run.id!!, row.migrationKey)
                recordRepository
                    .findById(key)
                    .flatMap { record -> recordRepository.update(record.apply { targetData = row.hashedValue }) }
                    .switchIfEmpty(recordRepository.save(RecRecord(key, targetData = row.hashedValue)))
            }

    fun runIgnoreFailure(datasetIds: List<String>): Flux<RecRun> = Flux.fromIterable(datasetIds)
        .filter { it.isNotEmpty() }
        .flatMap { runFor(it) }
        .onErrorContinue { err, it -> logger.warn(err) { "Start-up rec run failed for dataset [$it]." } }

    @Scheduled(initialDelay = "0s", fixedDelay = "1d")
    open fun scheduledStart() {
        runIgnoreFailure(config.triggerOnStart).subscribe()
    }
}
