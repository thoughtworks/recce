package recce.server.dataset

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.r2dbc.operations.R2dbcOperations
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import io.r2dbc.spi.Row
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux
import java.io.Serializable
import javax.persistence.*

@R2dbcRepository(dialect = Dialect.POSTGRES)
abstract class RecRecordRepository(private val operations: R2dbcOperations) :
    ReactorCrudRepository<RecRecord, RecRecordKey> {

    abstract fun findByIdRecRunId(id: Int): Flux<RecRecord>

    fun countMatchedByIdRecRunId(id: Int): Mono<MatchStatus> {
        return operations.withConnection { it.createStatement(countRecordsByStatus).bind("$1", id).execute() }
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
            MatchStatus::matched.name -> { st -> st.matched = count }
            MatchStatus::mismatched.name -> { st -> st.mismatched = count }
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
                            WHEN source_data = target_data THEN '${MatchStatus::matched.name}'
                            ELSE                                '${MatchStatus::mismatched.name}'
                        END AS $statusColumnName
                    FROM reconciliation_record
                    WHERE reconciliation_run_id = $1)
                SELECT $statusColumnName, count(*) AS "$countColumnName"
                FROM matching_data
                GROUP BY $statusColumnName;
            """.trimIndent()
    }

    data class MatchStatus(
        var sourceOnly: Int = 0,
        var targetOnly: Int = 0,
        var matched: Int = 0,
        var mismatched: Int = 0
    ) {
        val sourceTotal: Int
            get() = sourceOnly + matched + mismatched
        val targetTotal: Int
            get() = targetOnly + matched + mismatched
        val total: Int
            get() = sourceTotal + targetOnly
    }
}

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
