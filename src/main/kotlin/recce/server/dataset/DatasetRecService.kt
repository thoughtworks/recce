package recce.server.dataset

import jakarta.inject.Inject
import jakarta.inject.Singleton
import mu.KotlinLogging
import reactor.core.publisher.Mono
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2
import recce.server.config.DataLoadDefinition
import recce.server.config.DatasetConfiguration
import recce.server.config.RecConfiguration

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

        val datasetConfig = config.datasets[datasetId] ?: throw IllegalArgumentException("[$datasetId] not found!")

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
            .flatMap { result -> result.map(HashedRow::fromRow) }
            .zipWith(run.repeat())
            .flatMap { (row, run) ->
                recordRepository
                    .save(RecRecord(RecRecordKey(run.id!!, row.migrationKey), sourceData = row.hashedValue))
                    .map { row.lazyMeta() }
            }
            .defaultIfEmpty { DatasetMeta() }
            .last()
            .map { it.invoke() }

    private fun loadFromTarget(target: DataLoadDefinition, run: Mono<RecRun>): Mono<DatasetMeta> =
        target.runQuery()
            .flatMap { result -> result.map(HashedRow::fromRow) }
            .zipWith(run.repeat())
            .flatMap { (row, run) ->
                val key = RecRecordKey(run.id!!, row.migrationKey)
                recordRepository
                    .findById(key)
                    .flatMap { record -> recordRepository.update(record.apply { targetData = row.hashedValue }) }
                    .switchIfEmpty(recordRepository.save(RecRecord(key, targetData = row.hashedValue)))
                    .map { row.lazyMeta() }
            }
            .defaultIfEmpty { DatasetMeta() }
            .last()
            .map { it.invoke() }
}
