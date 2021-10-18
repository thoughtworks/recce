package com.thoughtworks.recce.server.dataset

import com.thoughtworks.recce.server.config.DataLoadDefinition
import com.thoughtworks.recce.server.config.ReconciliationConfiguration
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Inject
import jakarta.inject.Singleton
import mu.KotlinLogging
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2

private val logger = KotlinLogging.logger {}

@Singleton
open class ReconciliationService(
    @Inject private val config: ReconciliationConfiguration,
    private val runService: MigrationRunService,
    private val recordRepository: MigrationRecordRepository
) {
    fun runFor(dataSetId: String): Mono<MigrationRun> {

        val source = config.datasets[dataSetId]?.source
            ?: throw IllegalArgumentException("[$dataSetId] not found!")

        logger.info { "Starting reconciliation run for [$dataSetId]..." }

        val migrationRun = runService.start(dataSetId)

        return streamFromSource(source, migrationRun)
            .count()
            .flatMap { count -> migrationRun.map { it.apply { results = DataSetResults(count) } } }
            .flatMap { runService.complete(it) }
    }

    private fun streamFromSource(source: DataLoadDefinition, run: Mono<MigrationRun>): Flux<MigrationRecord> {
        return Flux.usingWhen(
            source.dbOperations.connectionFactory().create(),
            { it.createStatement(source.query).execute() },
            { it.close() }
        )
            .flatMap { result -> result.map(HashedRow::fromRow) }
            .zipWith(run.repeat())
            .map { (row, run) ->
                MigrationRecord(MigrationRecordKey(run.id!!, row.migrationKey)).apply {
                    sourceData = row.hashedValue
                }
            }
            .flatMap { record -> recordRepository.save(record) }
    }

    fun runIgnoreFailure(dataSetIds: List<String>): Flux<MigrationRun> {
        return Flux.fromIterable(dataSetIds)
            .filter { it.isNotEmpty() }
            .flatMap { runFor(it) }
            .onErrorContinue { err, it -> logger.warn(err) { "Start-up rec run failed for dataset [$it]." } }
            .doOnEach { logger.info { it.toString() } }
    }

    @Scheduled(initialDelay = "0s", fixedDelay = "1d")
    open fun scheduledStart() {
        runIgnoreFailure(config.triggerOnStart).subscribe()
    }
}
