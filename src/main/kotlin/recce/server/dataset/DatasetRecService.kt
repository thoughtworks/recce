package recce.server.dataset

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
        loadFromSource(datasetConfig.source, recRun)
            .zipWhen(
                { loadFromTarget(datasetConfig.target, recRun) },
                { source: DatasetResults, target: DatasetResults -> RecRunResults(source, target) }
            )

    private fun loadFromSource(source: DataLoadDefinition, run: Mono<RecRun>): Mono<DatasetResults> =
        source.runQuery()
            .flatMap { result -> result.map(HashedRow::fromRow) }
            .zipWith(run.repeat())
            .flatMap { (row, run) ->
                recordRepository
                    .save(RecRecord(RecRecordKey(run.id!!, row.migrationKey), sourceData = row.hashedValue))
                    .map { row::meta }
            }
            .reduce(DatasetResults(0)) { res, meta -> res.increment(meta) }

    private fun loadFromTarget(target: DataLoadDefinition, run: Mono<RecRun>): Mono<DatasetResults> =
        target.runQuery()
            .flatMap { result -> result.map(HashedRow::fromRow) }
            .zipWith(run.repeat())
            .flatMap { (row, run) ->
                val key = RecRecordKey(run.id!!, row.migrationKey)
                recordRepository
                    .findById(key)
                    .flatMap { record -> recordRepository.update(record.apply { targetData = row.hashedValue }) }
                    .switchIfEmpty(recordRepository.save(RecRecord(key, targetData = row.hashedValue)))
                    .map { row::meta }
            }
            .reduce(DatasetResults(0)) { res, meta -> res.increment(meta) }

    fun runIgnoreFailure(datasetIds: List<String>): Flux<RecRun> = Flux.fromIterable(datasetIds)
        .filter { it.isNotEmpty() }
        .flatMap { runFor(it) }
        .onErrorContinue { err, it -> logger.warn(err) { "Start-up rec run failed for dataset [$it]." } } // FIXME can hang if all have errors?
}
