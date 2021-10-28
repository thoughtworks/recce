package recce.server.dataset

import com.fasterxml.jackson.annotation.JsonIgnore
import io.micronaut.data.annotation.DateCreated
import io.micronaut.data.annotation.DateUpdated
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import java.time.Instant
import javax.persistence.*

@R2dbcRepository(dialect = Dialect.POSTGRES)
interface RecRunRepository : ReactorCrudRepository<RecRun, Int>

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

data class RecRunResults(
    val sourceMeta: DatasetMeta = DatasetMeta(),
    val targetMeta: DatasetMeta = DatasetMeta(),
    var summary: RecRecordRepository.MatchStatus? = null
)

data class DatasetMeta(val cols: List<ColMeta> = emptyList()) {
    @JsonIgnore
    fun isEmpty() = cols.isEmpty()
}

data class ColMeta(val name: String, val javaType: String)
