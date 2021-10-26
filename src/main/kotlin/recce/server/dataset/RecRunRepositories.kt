package recce.server.dataset

import io.micronaut.data.annotation.DateCreated
import io.micronaut.data.annotation.DateUpdated
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import reactor.core.publisher.Flux
import java.io.Serializable
import java.time.Instant
import javax.persistence.*

@R2dbcRepository(dialect = Dialect.POSTGRES)
interface RecRunRepository : ReactorCrudRepository<RecRun, Int>

@R2dbcRepository(dialect = Dialect.POSTGRES)
interface RecRecordRepository : ReactorCrudRepository<RecRecord, RecRecordKey> {
    fun findByIdRecRunId(id: Int): Flux<RecRecord>
}

@Entity
@Table(name = "reconciliation_run")
data class RecRun(
    @Id @GeneratedValue val id: Int? = null,
    val datasetId: String,
    @DateCreated val createdTime: Instant? = null,
    @DateUpdated var updatedTime: Instant? = null,
    var completedTime: Instant? = null,
) {
    constructor(datasetId: String) : this(null, datasetId)

    @Transient
    var results: RecRunResults? = null
}

data class RecRunResults(val source: DatasetResults, val target: DatasetResults) {
    constructor(sourceRows: Long, targetRows: Long) : this(DatasetResults(sourceRows), DatasetResults(targetRows))
}

data class DatasetResults(val rows: Long, val meta: DatasetMeta = DatasetMeta()) {
    fun increment(meta: DatasetMeta): DatasetResults {
        return DatasetResults(rows + 1, if (this.meta.isEmpty()) meta else this.meta)
    }
}

data class DatasetMeta(val cols: List<ColMeta> = emptyList()) {
    fun isEmpty() = cols.isEmpty()
}

data class ColMeta(val name: String, val javaType: String)

@Entity
@Table(name = "reconciliation_record")
data class RecRecord(
    @EmbeddedId val id: RecRecordKey,
    var sourceData: String? = null,
    var targetData: String? = null
)

@Embeddable
data class RecRecordKey(
    @Column(name = "reconciliation_run_id") val recRunId: Int,
    @Column(name = "migration_key") val migrationKey: String
) : Serializable
