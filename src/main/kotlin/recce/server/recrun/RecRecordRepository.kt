package recce.server.recrun

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.r2dbc.operations.R2dbcOperations
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import io.r2dbc.spi.Row
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux
import javax.persistence.*

@R2dbcRepository(dialect = Dialect.POSTGRES)
abstract class RecRecordRepository(private val operations: R2dbcOperations) :
    ReactorCrudRepository<RecRecord, Int> {

    @Suppress("MicronautDataRepositoryMethodParameters") // False positive
    abstract fun updateByRecRunIdAndMigrationKey(recRunId: Int, migrationKey: String, targetData: String?): Mono<Void>

    abstract fun findByRecRunIdAndMigrationKeyIn(recRunId: Int, migrationKeys: List<String>): Flux<RecRecord>

    abstract fun findByRecRunId(recRunId: Int): Flux<RecRecord>

    fun countMatchedByKeyRecRunId(recRunId: Int): Mono<MatchStatus> {
        return operations.withConnection { it.createStatement(countRecordsByStatus).bind("$1", recRunId).execute() }
            .toFlux()
            .flatMap { res -> res.map { row, _ -> matchStatusSetterFor(row) } }
            .reduce(MatchStatus()) { status, propSet -> propSet.invoke(status); status }
    }

    private fun matchStatusSetterFor(row: Row): (MatchStatus) -> Unit {
        val count = row.get(countColumnName, Number::class.java)?.toInt()
            ?: throw IllegalArgumentException("Missing [$countColumnName] column!")

        return when (val status = row.get(statusColumnName)) {
            MatchStatus::sourceOnly.name -> { st -> st.sourceOnly = count }
            MatchStatus::targetOnly.name -> { st -> st.targetOnly = count }
            MatchStatus::bothMatched.name -> { st -> st.bothMatched = count }
            MatchStatus::bothMismatched.name -> { st -> st.bothMismatched = count }
            else -> throw IllegalArgumentException("Invalid $statusColumnName [$status]")
        }
    }

    companion object {
        private const val statusColumnName = "match_status"
        private const val countColumnName = "count"

        private val countRecordsByStatus =
            """
                WITH matching_data AS 
                    (SELECT migration_key,
                        CASE 
                            WHEN target_data IS NULL       THEN '${MatchStatus::sourceOnly.name}'
                            WHEN source_data IS NULL       THEN '${MatchStatus::targetOnly.name}'
                            WHEN source_data = target_data THEN '${MatchStatus::bothMatched.name}'
                            ELSE                                '${MatchStatus::bothMismatched.name}'
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
    @Id @GeneratedValue val id: Int? = null,
    @Column(name = "reconciliation_run_id") val recRunId: Int,
    @Column(name = "migration_key") val migrationKey: String,
    var sourceData: String? = null,
    var targetData: String? = null
) {
    constructor(key: RecRecordKey, sourceData: String? = null, targetData: String? = null) :
        this(recRunId = key.recRunId, migrationKey = key.migrationKey, sourceData = sourceData, targetData = targetData)

    @Transient
    val key = RecRecordKey(recRunId, migrationKey)
}

data class RecRecordKey(val recRunId: Int, val migrationKey: String)
