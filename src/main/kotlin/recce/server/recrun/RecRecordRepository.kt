package recce.server.recrun

import io.micronaut.data.annotation.Query
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.r2dbc.operations.R2dbcOperations
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import io.r2dbc.spi.Row
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux
import javax.persistence.*
import kotlin.reflect.KMutableProperty1

// Declared as an interface to make it possible to replace the bean with a mock in tests
// The replacement doesn't seem to work with Micronaut Test with an abstract class
interface RecRecordRepository : ReactorCrudRepository<RecRecord, Int> {
    fun findByRecRunIdAndMigrationKeyIn(recRunId: Int, migrationKeys: List<String>): Flux<RecRecord>
    fun findByRecRunId(recRunId: Int): Flux<RecRecord>

    @Query(
        """
        (SELECT * FROM reconciliation_record r WHERE r.reconciliation_run_id = :recRunId AND r.target_data IS NULL LIMIT :limit)
        UNION
        (SELECT * FROM reconciliation_record r WHERE r.reconciliation_run_id = :recRunId AND r.source_data IS NULL LIMIT :limit)
        UNION
        (SELECT * FROM reconciliation_record r WHERE r.reconciliation_run_id = :recRunId AND r.source_data <> r.target_data LIMIT :limit)
        """
    )
    fun findFirstByRecRunIdSplitByMatchStatus(recRunId: Int, limit: Int = 10): Flux<RecRecord>
    fun countMatchedByKeyRecRunId(recRunId: Int): Mono<MatchStatus>
}

@R2dbcRepository(dialect = Dialect.POSTGRES)
internal abstract class AbstractRecRecordRepository(private val operations: R2dbcOperations) : RecRecordRepository {

    override fun countMatchedByKeyRecRunId(recRunId: Int): Mono<MatchStatus> {
        return operations.withConnection { it.createStatement(countRecordsByStatus).bind("$1", recRunId).execute() }
            .toFlux()
            .flatMap { res -> res.map { row, _ -> matchStatusSetterFor(row) } }
            .reduce(MatchStatus()) { status, propSet -> propSet(status); status }
    }

    private fun matchStatusSetterFor(row: Row): (MatchStatus) -> Unit {
        val count = row.get(countColumnName, Number::class.java)?.toInt()
            ?: throw IllegalArgumentException("Missing [$countColumnName] column!")

        val recordMatchStatus = RecordMatchStatus.valueOf(row.get(statusColumnName, String::class.java) ?: throw IllegalArgumentException("Missing [$statusColumnName] column!"))
        return { st -> recordMatchStatus.setter(st, count) }
    }

    companion object {
        private const val statusColumnName = "match_status"
        private const val countColumnName = "count"

        private val countRecordsByStatus =
            """
                WITH matching_data AS 
                    (SELECT migration_key,
                        CASE 
                            WHEN target_data IS NULL       THEN '${RecordMatchStatus.SourceOnly}'
                            WHEN source_data IS NULL       THEN '${RecordMatchStatus.TargetOnly}'
                            WHEN source_data = target_data THEN '${RecordMatchStatus.BothMatched}'
                            ELSE                                '${RecordMatchStatus.BothMismatched}'
                        END AS $statusColumnName
                    FROM reconciliation_record
                    WHERE reconciliation_run_id = $1)
                SELECT $statusColumnName, count(*) AS "$countColumnName"
                FROM matching_data
                GROUP BY $statusColumnName;
            """.trimIndent()
    }
}

@Entity
@Table(name = "reconciliation_record")
data class RecRecord(
    @Id @GeneratedValue
    val id: Int? = null,
    @Column(name = "reconciliation_run_id") val recRunId: Int,
    @Column(name = "migration_key") val migrationKey: String,
    var sourceData: String? = null,
    var targetData: String? = null
) {
    constructor(key: RecRecordKey, sourceData: String? = null, targetData: String? = null) :
        this(recRunId = key.recRunId, migrationKey = key.migrationKey, sourceData = sourceData, targetData = targetData)

    @Transient
    val key = RecRecordKey(recRunId, migrationKey)

    @get:Transient
    val matchStatus: RecordMatchStatus
        get() = RecordMatchStatus.from(this)
}

enum class RecordMatchStatus(val setter: KMutableProperty1.Setter<MatchStatus, Int>) {
    SourceOnly(MatchStatus::sourceOnly.setter),
    TargetOnly(MatchStatus::targetOnly.setter),
    BothMatched(MatchStatus::bothMatched.setter),
    BothMismatched(MatchStatus::bothMismatched.setter);

    companion object {
        fun from(record: RecRecord) = when {
            record.targetData == null -> SourceOnly
            record.sourceData == null -> TargetOnly
            record.sourceData == record.targetData -> BothMatched
            else -> BothMismatched
        }
    }
}

data class RecRecordKey(val recRunId: Int, val migrationKey: String)
