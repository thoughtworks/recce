package com.thoughtworks.recce.server.dataset

import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.EmbeddedId
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import javax.persistence.Column

@R2dbcRepository(dialect = Dialect.H2)
interface MigrationRecordRepository : ReactorCrudRepository<MigrationRecord, MigrationRecordKey>

@MappedEntity("data_set_migration_record")
data class MigrationRecord(
    @EmbeddedId val id: MigrationRecordKey,
    var sourceData: String? = null,
    var targetData: String? = null
)

@Embeddable
data class MigrationRecordKey(
    @Column(name = "data_set_id") val dataSetId: String,
    @Column(name = "migration_key") val migrationKey: String
)
