package recce.server.dataset

import jakarta.inject.Inject
import jakarta.inject.Singleton
import mu.KotlinLogging
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2
import recce.server.BuildInfoConfiguration
import recce.server.R2dbcConfiguration
import recce.server.RecConfiguration
import recce.server.recrun.*

private val logger = KotlinLogging.logger {}

interface DatasetRecRunner {
    fun runFor(datasetId: String): Mono<RecRun>
}

interface DatasetConfigProvider {
    val availableDataSets: Collection<DatasetConfiguration>
}

@Singleton
open class DatasetRecService(
    @Inject private val recConfig: RecConfiguration,
    @Inject private val r2dbcConfig: R2dbcConfiguration,
    @Inject private val buildConfig: BuildInfoConfiguration,
    private val runService: RecRunService,
    private val recordRepository: RecRecordRepository
) : DatasetRecRunner, DatasetConfigProvider {
    override val availableDataSets = recConfig.datasets.values

    override fun runFor(datasetId: String): Mono<RecRun> {
        val datasetConfig = recConfig.datasets[datasetId]
            ?: throw IllegalArgumentException("Dataset definition [$datasetId] not found!")

        logger.info { "Starting reconciliation run for [$datasetId]..." }

        val metadata = generateMetadata(datasetConfig)
        val recRun = runService.start(datasetId, metadata)

        return loadFrom(datasetConfig.source, datasetConfig.resolvedHashingStrategy, recRun, this::saveSourceBatch)
            .zipWhen { loadFrom(datasetConfig.target, datasetConfig.resolvedHashingStrategy, recRun, this::saveTargetBatch) }
            .flatMap { (source, target) -> recRun.map { it.withMetaData(source, target) } }
            .flatMap { run -> runService.successful(run) }
            .onErrorResume { error -> recRun.flatMap { run -> runService.failed(run, error) } }
    }

    private fun loadFrom(
        def: DataLoadDefinition,
        hashingStrategy: HashingStrategy,
        run: Mono<RecRun>,
        batchSaver: (List<HashedRow>, RecRun) -> Flux<LazyDatasetMeta>
    ): Mono<DatasetMeta> =
        def.runQuery()
            .doFirst { logger.info { "${def.datasourceDescriptor} query completed; streaming to Recce DB" } }
            .single()
            .flatMapMany { it.map(hashingStrategy::hash) }
            .buffer(recConfig.defaults.batchSize)
            .zipWith(run.repeat())
            .flatMap({ (rows, run) -> batchSaver(rows, run) }, recConfig.defaults.batchConcurrency)
            .onErrorMap { DataLoadException("Failed to load data from ${def.datasourceDescriptor}: ${it.message}", it) }
            .defaultIfEmpty { DatasetMeta() }
            .last()
            .map { meta -> meta() }
            .doOnNext { logger.info { "Load from ${def.datasourceDescriptor} completed" } }

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

    private fun generateMetadata(datasetConfig: DatasetConfiguration): Map<String, String> = mapOf(
        "sourceQuery" to datasetConfig.source.query,
        "targetQuery" to datasetConfig.target.query,
        "sourceUrl" to r2dbcConfig.getUrl(datasetConfig.source.datasourceRef),
        "targetUrl" to r2dbcConfig.getUrl(datasetConfig.target.datasourceRef),
        "version" to buildConfig.version
    )
}

class DataLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
