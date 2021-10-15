package com.thoughtworks.recce.server.dataset

import com.thoughtworks.recce.server.config.DataLoadDefinition
import com.thoughtworks.recce.server.config.ReconciliationConfiguration
import io.micronaut.discovery.event.ServiceReadyEvent
import io.micronaut.runtime.event.annotation.EventListener
import io.micronaut.scheduling.annotation.Async
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
    @Inject private val runService: MigrationRunService,
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

    @EventListener
    @Async
    open fun doOnStart(event: ServiceReadyEvent): Mono<MigrationRun> {
        return runFor("test-dataset")
            .doOnEach { logger.info { it.toString() } }
    }
}
