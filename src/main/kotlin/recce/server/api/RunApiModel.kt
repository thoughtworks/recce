package recce.server.api

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.core.annotation.Introspected
import recce.server.recrun.DatasetMeta
import recce.server.recrun.MatchStatus
import recce.server.recrun.RecRun
import java.time.Duration
import java.time.Instant

@Introspected
data class RunApiModel(
    val id: Int,
    val datasetId: String,
    val createdTime: Instant,
    val completedTime: Instant?,
    val summary: Summary?
) {
    constructor(run: RecRun) : this(
        id = run.id!!,
        datasetId = run.datasetId,
        createdTime = run.createdTime!!,
        completedTime = run.completedTime,
        summary = run.summary?.let { Summary(it, run.sourceMeta, run.targetMeta) }
    )

    @get:JsonProperty("completedDurationSeconds")
    val completedDuration: Duration?
        get() = if (completedTime != null) Duration.between(createdTime, completedTime) else null
}

data class Summary(
    val totalRowCount: Int,
    val bothMatchedCount: Int,
    val bothMismatchedCount: Int,
    val source: IndividualDbResult,
    val target: IndividualDbResult,
    val bothMismatchedMigrationKeySample: List<String>? = null
) {
    constructor(matchStatus: MatchStatus, sourceMeta: DatasetMeta, targetMeta: DatasetMeta) : this(
        matchStatus.total,
        matchStatus.bothMatched,
        matchStatus.bothMismatched,
        IndividualDbResult(matchStatus.sourceTotal, matchStatus.sourceOnly, sourceMeta),
        IndividualDbResult(matchStatus.targetTotal, matchStatus.targetOnly, targetMeta),
    )
}

data class IndividualDbResult(
    val totalRowCount: Int,
    val onlyHereCount: Int,
    val meta: DatasetMeta,
    val onlyMigrationKeySample: List<String>? = null,
)
