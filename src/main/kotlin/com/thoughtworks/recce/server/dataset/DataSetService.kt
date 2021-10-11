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
    private val recordRepository: MigrationRecordRepository
) {
    fun start(dataSetName: String): Mono<DataSetResults> {

        val source = config.datasets[dataSetName]?.source
            ?: throw IllegalArgumentException("[$dataSetName] not found!")

        logger.info { "Streaming [$dataSetName] from [source]..." }

        return Mono.from(source.dbOperations.connectionFactory().create())
            .flatMapMany { it.createStatement(source.query).execute() }
            .flatMap { result -> result.map(::toHashedRow) }
            .map { row ->
                MigrationRecord(MigrationRecordKey(dataSetName, row.migrationKey)).apply {
                    sourceData = row.hashedValue
                }
            }
            .flatMap { record -> recordRepository.save(record) }
            .count()
            .map { DataSetResults(it) }
    }

    @EventListener
    @Async
    open fun doOnStart(event: ServiceReadyEvent): Mono<DataSetResults> {
        return start("test-dataset")
            .doOnEach { logger.info { it.toString() } }
    }
}

data class DataSetResults(val sourceRowsInserted: Long)
