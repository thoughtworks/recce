package recce.server.dataset

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
    @Embedded var summary: MatchStatus? = null
) {
    constructor(datasetId: String) : this(null, datasetId)

    @Transient
    var sourceMeta: DatasetMeta = DatasetMeta()
    @Transient
    var targetMeta: DatasetMeta = DatasetMeta()
}

data class DatasetMeta(val cols: List<ColMeta> = emptyList())

data class ColMeta(val name: String, val javaType: String)

@Embeddable
data class MatchStatus(
    var sourceOnly: Int = 0,
    var targetOnly: Int = 0,
    var bothMatched: Int = 0,
    var bothMismatched: Int = 0
) {
    @get:Transient
    val sourceTotal: Int
        get() = sourceOnly + bothMatched + bothMismatched

    @get:Transient
    val targetTotal: Int
        get() = targetOnly + bothMatched + bothMismatched

    @get:Transient
    val total: Int
        get() = sourceTotal + targetOnly
}
