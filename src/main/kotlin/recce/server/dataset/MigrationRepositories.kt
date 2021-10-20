package recce.server.dataset

import io.micronaut.data.annotation.DateCreated
import io.micronaut.data.annotation.DateUpdated
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import reactor.core.publisher.Flux
import java.io.Serializable
import java.time.LocalDateTime
import javax.persistence.*

@R2dbcRepository(dialect = Dialect.POSTGRES)
interface MigrationRunRepository : ReactorCrudRepository<MigrationRun, Int>

@R2dbcRepository(dialect = Dialect.POSTGRES)
interface MigrationRecordRepository : ReactorCrudRepository<MigrationRecord, MigrationRecordKey> {
    fun findByIdMigrationId(id: Int): Flux<MigrationRecord>
}

@Entity
@Table(name = "dataset_migration_run")
data class MigrationRun(
    @Id @GeneratedValue val id: Int?,
    val datasetId: String,
    @DateCreated val createdTime: LocalDateTime?,
) {
    @DateUpdated
    var updatedTime: LocalDateTime? = null
    var completedTime: LocalDateTime? = null

    constructor(datasetId: String) : this(null, datasetId, null)

    @Transient
    var results: DatasetResults? = null
}

data class DatasetResults(val sourceRows: Long, val targetRows: Long)

@Entity
@Table(name = "dataset_migration_record")
data class MigrationRecord(
    @EmbeddedId val id: MigrationRecordKey,
    var sourceData: String? = null,
    var targetData: String? = null
)

@Embeddable
data class MigrationRecordKey(
    @Column(name = "migration_id") val migrationId: Int,
    @Column(name = "migration_key") val migrationKey: String
) : Serializable
