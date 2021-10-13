package com.thoughtworks.recce.server.dataset

import com.thoughtworks.recce.server.config.ReconciliationConfiguration
import io.micronaut.discovery.event.ServiceReadyEvent
import io.micronaut.runtime.event.annotation.EventListener
import io.micronaut.scheduling.annotation.Async
import jakarta.inject.Inject
import jakarta.inject.Singleton
import mu.KotlinLogging
import reactor.core.publisher.Mono

private val logger = KotlinLogging.logger {}

@Singleton
open class DataSetService(
    @Inject private val config: ReconciliationConfiguration,
    private val runRepository: MigrationRunRepository,
    private val recordRepository: MigrationRecordRepository
) {
    fun start(dataSetId: String): Mono<MigrationRun> {

        val source = config.datasets[dataSetId]?.source
            ?: throw IllegalArgumentException("[$dataSetId] not found!")

        logger.info { "Starting reconciliation run for [$dataSetId]..." }

        // TODO Make this properly async/reactive
        val migrationRun = runRepository.save(MigrationRun(dataSetId)).block()!!

        logger.info { "Streaming $migrationRun from [source]..." }

        return Mono.from(source.dbOperations.connectionFactory().create())
            .flatMapMany { it.createStatement(source.query).execute() }
            .flatMap { result -> result.map(::toHashedRow) }
            .map { row ->
                MigrationRecord(MigrationRecordKey(migrationRun.id!!, row.migrationKey)).apply {
                    sourceData = row.hashedValue
                }
            }
            .flatMap { record -> recordRepository.save(record) }
            .count()
            .map { migrationRun.apply { results = DataSetResults(it) } }
    }

    @EventListener
    @Async
    open fun doOnStart(event: ServiceReadyEvent): Mono<MigrationRun> {
        return start("test-dataset")
            .doOnEach { logger.info { it.toString() } }
    }
}
