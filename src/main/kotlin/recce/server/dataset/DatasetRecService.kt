package recce.server.dataset

import jakarta.inject.Inject
import jakarta.inject.Singleton
import mu.KotlinLogging
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux
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

        return loadFrom(datasetConfig.source, recRun, this::saveSourceBatch)
            .zipWhen { loadFrom(datasetConfig.target, recRun, this::saveTargetBatch) }
            .flatMap { (source, target) -> recRun.map { it.withMetaData(source, target) } }
            .flatMap { run -> runService.complete(run) }
    }

    private fun loadFrom(
        def: DataLoadDefinition,
        run: Mono<RecRun>,
        batchSaver: (List<HashedRow>, RecRun) -> Flux<LazyDatasetMeta>
    ): Mono<DatasetMeta> =
        def.runQuery()
            .doOnNext { logger.info { "${def.role} query completed; streaming to Recce DB" } }
            .flatMap { result -> result.map(HashedRow::fromRow) }
            .buffer(config.defaultBatchSize)
            .zipWith(run.repeat())
            .flatMap({ (rows, run) -> batchSaver(rows, run) }, config.defaultBatchConcurrency)
            .onErrorMap { DataLoadException("Failed to load data from ${def.role} [${def.dataSourceRef}]: ${it.message}", it) }
            .defaultIfEmpty { DatasetMeta() }
            .last()
            .map { meta -> meta() }
            .doOnNext { logger.info { "Load from ${def.role} completed" } }

    private fun saveSourceBatch(rows: List<HashedRow>, run: RecRun): Flux<LazyDatasetMeta> {
        val records = rows.map { RecRecord(key = RecRecordKey(run.id!!, it.migrationKey), sourceData = it.hashedValue) }
        return recordRepository
            .saveAll(records)
            .index()
            .map { (i, _) -> rows[i.toInt()].lazyMeta() }
    }

    private fun saveTargetBatch(rows: List<HashedRow>, run: RecRun): Flux<LazyDatasetMeta> {
        val toPersist = rows.associateByTo(mutableMapOf()) { it.migrationKey }
        val updateExistingRecords = recordRepository
            .findByRecRunIdAndMigrationKeyIn(run.id!!, rows.map { it.migrationKey })
            .map { it.apply { targetData = toPersist.remove(it.migrationKey)!!.hashedValue } }
            .collectList()
            .toFlux()
            .flatMap { if (it.isEmpty()) Flux.empty() else recordRepository.updateAll(it) }
        val saveNewRecords = Flux
            .defer { Mono.just(toPersist.values) }
            .map { hashedRows ->
                hashedRows.map { row ->
                    RecRecord(
                        recRunId = run.id,
                        migrationKey = row.migrationKey,
                        targetData = row.hashedValue
                    )
                }
            }.flatMap { if (it.isEmpty()) Flux.empty() else recordRepository.saveAll(it) }

        return updateExistingRecords.concatWith(saveNewRecords).map { rows.first().lazyMeta() }
    }
}

class DataLoadException(message: String, cause: Throwable) : Exception(message, cause)
