package recce.server.dataset

import com.fasterxml.jackson.annotation.JsonIgnore
import io.micronaut.data.annotation.DateCreated
import io.micronaut.data.annotation.DateUpdated
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.r2dbc.operations.R2dbcOperations
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux
import java.io.Serializable
import java.time.Instant
import javax.persistence.*

@R2dbcRepository(dialect = Dialect.POSTGRES)
interface RecRunRepository : ReactorCrudRepository<RecRun, Int>

@R2dbcRepository(dialect = Dialect.POSTGRES)
abstract class RecRecordRepository(private val operations: R2dbcOperations) :
    ReactorCrudRepository<RecRecord, RecRecordKey> {
    abstract fun findByIdRecRunId(id: Int): Flux<RecRecord>

    fun countMatchedByIdRecRunId(id: Int): Mono<MatchStatus> {
        val sql =
            """
                WITH matching_data as (SELECT migration_key,
                                              CASE
                                                  WHEN source_data is null THEN 'MISSING_SOURCE'
                                                  WHEN target_data is null THEN 'MISSING_TARGET'
                                                  WHEN source_data = target_data THEN 'MATCHED'
                                                  ELSE 'MISMATCHED'
                                                  END AS match_status
                                       from reconciliation_record
                                       where reconciliation_run_id = $1)
                SELECT match_status, count(*) as count
                FROM matching_data
                GROUP BY match_status;
            """.trimIndent()
        val result: Flux<out io.r2dbc.spi.Result> =
            operations.withConnection { it.createStatement(sql).bind("$1", id).execute() }.toFlux()

        return result.flatMap { res ->
            res.map { row, _ ->
                val count = row.get("count", Number::class.java)?.toInt()
                    ?: throw IllegalArgumentException("Missing [count] column!")
                when (val status = row.get("match_status")) {
                    "MISSING_SOURCE" -> MatchStatus(missing_source = count)
                    "MISSING_TARGET" -> MatchStatus(missing_target = count)
                    "MATCHED" -> MatchStatus(matched = count)
                    "MISMATCHED" -> MatchStatus(mismatched = count)
                    else -> throw IllegalArgumentException("Invalid match_status [$status]")
                }
            }
        }.reduce { first, second -> first + second }
            .defaultIfEmpty(MatchStatus())
    }

    data class MatchStatus(
        var missing_source: Int = 0,
        var missing_target: Int = 0,
        var matched: Int = 0,
        var mismatched: Int = 0
    ) {
        operator fun plus(increment: MatchStatus): MatchStatus {
            return MatchStatus(
                missing_source + increment.missing_source,
                missing_target + increment.missing_target,
                matched + increment.matched,
                mismatched + increment.mismatched
            )
        }
    }
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

data class RecRunResults(
    val source: DatasetResults,
    val target: DatasetResults,
    var summary: RecRecordRepository.MatchStatus? = null
) {
    constructor(sourceRows: Long, targetRows: Long) : this(DatasetResults(sourceRows), DatasetResults(targetRows))
}

data class DatasetResults(var rows: Long, var meta: DatasetMeta = DatasetMeta()) {
    fun increment(metaSupplier: () -> DatasetMeta): DatasetResults {
        rows++
        if (this.meta.isEmpty()) meta = metaSupplier.invoke()
        return this
    }
}

data class DatasetMeta(val cols: List<ColMeta> = emptyList()) {
    @JsonIgnore
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
