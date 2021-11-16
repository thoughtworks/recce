package recce.server.dataset

import jakarta.inject.Inject
import jakarta.inject.Singleton
import mu.KotlinLogging
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2
import recce.server.RecConfiguration
import recce.server.recrun.*

private val logger = KotlinLogging.logger {}

interface DatasetRecRunner {
    fun runFor(datasetId: String): Mono<RecRun>
}

@Singleton
open class DatasetRecService(
    @Inject private val config: RecConfiguration,
    private val runService: RecRunService,
    private val recordRepository: RecRecordRepository
) : DatasetRecRunner {
    override fun runFor(datasetId: String): Mono<RecRun> {

        val datasetConfig = config.datasets[datasetId] ?: throw IllegalArgumentException("Dataset definition [$datasetId] not found!")

        logger.info { "Starting reconciliation run for [$datasetId]..." }

        val recRun = runService.start(datasetId)

        return loadFromSourceThenTarget(datasetConfig, recRun)
            .flatMap { (source, target) ->
                recRun.map {
                    it.apply {
                        sourceMeta = source
                        targetMeta = target
                    }
                }
            }
            .flatMap { run -> runService.complete(run) }
    }

    private fun loadFromSourceThenTarget(datasetConfig: DatasetConfiguration, recRun: Mono<RecRun>) =
        loadFromSource(datasetConfig.source, recRun)
            .zipWhen { loadFromTarget(datasetConfig.target, recRun) }

    private fun loadFromSource(source: DataLoadDefinition, run: Mono<RecRun>): Mono<DatasetMeta> =
        source.runQuery()
            .doOnNext { logger.info { "Source query completed; streaming to Recce DB" } }
            .flatMap { result -> result.map(HashedRow::fromRow) }
            .buffer(config.defaultBatchSize)
            .zipWith(run.repeat())
            .flatMap { (rows, run) ->
                val records = rows.map { RecRecord(key = RecRecordKey(run.id!!, it.migrationKey), sourceData = it.hashedValue) }
                recordRepository
                    .saveAll(records)
                    .index()
                    .map { (i, _) -> rows[i.toInt()].lazyMeta() }
            }
            .onErrorMap { DataLoadException("Failed to load data from source [${source.dataSourceRef}]: ${it.message}", it) }
            .defaultIfEmpty { DatasetMeta() }
            .last()
            .map { it.invoke() }
            .doOnNext { logger.info { "Load from source completed" } }

    private fun loadFromTarget(target: DataLoadDefinition, run: Mono<RecRun>): Mono<DatasetMeta> {
        val bufferedRows = target.runQuery()
            .doOnNext { logger.info { "Target query completed; streaming to Recce DB" } }
            .flatMap { result -> result.map(HashedRow::fromRow) }
            .buffer(config.defaultBatchSize)
            .zipWith(run.repeat())
        val batches: Flux<() -> DatasetMeta> = bufferedRows
            .flatMap { (rows, run) ->
//                logger.info { "Processing batch of size [${rows.size}] from target"}
                val toPersist = rows.associateByTo(mutableMapOf()) { it.migrationKey }
                val updatedRows = recordRepository
                    .findByRecRunIdAndMigrationKeyIn(run.id!!, rows.map { it.migrationKey })
                    .flatMap { found ->
                        recordRepository.updateByRecRunIdAndMigrationKey(
                            found.recRunId,
                            found.migrationKey,
                            targetData = toPersist.remove(found.migrationKey)?.hashedValue
                        ).then(Mono.just(found))
                    }
                val newRows = Flux
                    .defer {
//                        logger.info { "Checking new rows for batch of size [${toPersist.size}]" }
                        if (toPersist.isEmpty()) Mono.empty() else Mono.just(toPersist.values)
                    }
                    .map { hashedRows ->
                        hashedRows.map {
                            RecRecord(
                                recRunId = run.id,
                                migrationKey = it.migrationKey,
                                targetData = it.hashedValue
                            )
                        }
                    }.flatMap { recordRepository.saveAll(it) }

                updatedRows.concatWith(newRows).map { rows.first().lazyMeta() }
            }
        return batches
            .doOnNext { logger.debug { "$it emitted" } }
            .onErrorMap { DataLoadException("Failed to load data from target [${target.dataSourceRef}]: ${it.message}", it) }
            .defaultIfEmpty { DatasetMeta() }
            .last()
            .map { it.invoke() }
            .doOnNext { logger.info { "Load from target completed" } }
    }
}

class DataLoadException(message: String, cause: Throwable) : Exception(message, cause)
