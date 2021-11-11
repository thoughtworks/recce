package recce.server.dataset

import jakarta.inject.Inject
import jakarta.inject.Singleton
import mu.KotlinLogging
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
                val records = rows.map { RecRecord(RecRecordKey(run.id!!, it.migrationKey), sourceData = it.hashedValue) }
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

    private fun loadFromTarget(target: DataLoadDefinition, run: Mono<RecRun>): Mono<DatasetMeta> =
        target.runQuery()
            .doOnNext { logger.info { "Target query completed; streaming to Recce DB" } }
            .flatMap { result -> result.map(HashedRow::fromRow) }
            .zipWith(run.repeat())
            .flatMap { (row, run) ->
                val key = RecRecordKey(run.id!!, row.migrationKey)
                recordRepository
                    .existsById(key)
                    .flatMap { exists ->
                        when (exists) {
                            true -> recordRepository.update(key, targetData = row.hashedValue).thenReturn(row)
                            false -> recordRepository.save(RecRecord(key, targetData = row.hashedValue))
                        }
                    }
                    .then(Mono.just(row.lazyMeta()))
            }
            .onErrorMap { DataLoadException("Failed to load data from target [${target.dataSourceRef}]: ${it.message}", it) }
            .defaultIfEmpty { DatasetMeta() }
            .last()
            .map { it.invoke() }
            .doOnNext { logger.info { "Load from target completed" } }
}

class DataLoadException(message: String, cause: Throwable) : Exception(message, cause)
