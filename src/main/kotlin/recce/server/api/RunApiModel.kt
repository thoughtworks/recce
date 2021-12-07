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
    @get:JsonProperty("completedDurationSeconds")
    val completedDuration: Duration?
        get() = if (completedTime != null) Duration.between(createdTime, completedTime) else null

    data class Builder(
        private val run: RecRun,
        private val summaryBuilder: Summary.Builder = Summary.Builder().matchStatus(run.summary)
    ) {

        fun migrationKeySamples(migrationKeySamples: Map<String, List<String>>) = apply { summaryBuilder.migrationKeySamples(migrationKeySamples) }

        fun build() = RunApiModel(
            id = run.id!!,
            datasetId = run.datasetId,
            createdTime = run.createdTime!!,
            completedTime = run.completedTime,
            summary = summaryBuilder
                .sourceMeta(run.sourceMeta)
                .targetMeta(run.targetMeta)
                .build()
        )
    }
}

data class Summary(
    val totalRowCount: Int,
    val bothMatchedCount: Int,
    val bothMismatchedCount: Int,
    val source: IndividualDbResult,
    val target: IndividualDbResult,
    var bothMismatchedMigrationKeySample: List<String>? = null
) {
    data class Builder(
        private var matchStatus: MatchStatus? = null,
        private var sourceMeta: DatasetMeta? = null,
        private var targetMeta: DatasetMeta? = null,

        private var migrationKeySamples: Map<String, List<String>>? = null
    ) {
        fun matchStatus(matchStatus: MatchStatus?) = apply { this.matchStatus = matchStatus }
        fun sourceMeta(sourceMeta: DatasetMeta) = apply { this.sourceMeta = sourceMeta }
        fun targetMeta(targetMeta: DatasetMeta) = apply { this.targetMeta = targetMeta }
        fun migrationKeySamples(migrationKeySamples: Map<String, List<String>>) = apply { this.migrationKeySamples = migrationKeySamples }

        fun build() = matchStatus?.let {
            Summary(
                matchStatus!!.total,
                matchStatus!!.bothMatched,
                matchStatus!!.bothMismatched,
                IndividualDbResult(matchStatus!!.sourceTotal, matchStatus!!.sourceOnly, sourceMeta),
                IndividualDbResult(matchStatus!!.targetTotal, matchStatus!!.targetOnly, targetMeta),
            ).apply {
                migrationKeySamples?.let {
                    bothMismatchedMigrationKeySample = migrationKeySamples!!["both"]
                    source.onlyMigrationKeySample = migrationKeySamples!!["source"]
                    target.onlyMigrationKeySample = migrationKeySamples!!["target"]
                }
            }
        }
    }
}

data class IndividualDbResult(
    val totalRowCount: Int,
    val onlyHereCount: Int,
    val meta: DatasetMeta?,
    var onlyMigrationKeySample: List<String>? = null,
)
