package recce.server.api

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.core.annotation.Introspected
import recce.server.recrun.DatasetMeta
import recce.server.recrun.MatchStatus
import recce.server.recrun.RecRun
import recce.server.recrun.RecordMatchStatus
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
        fun migrationKeySamples(migrationKeySamples: Map<RecordMatchStatus, List<String>>) = apply { summaryBuilder.migrationKeySamples(migrationKeySamples) }

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
    val totalCount: Int,
    val bothMatchedCount: Int,
    val bothMismatchedCount: Int,
    var bothMismatchedSampleKeys: List<String>? = null,
    val source: IndividualDbResult,
    val target: IndividualDbResult
) {
    data class Builder(
        private var matchStatus: MatchStatus? = null,
        private var sourceMeta: DatasetMeta? = null,
        private var targetMeta: DatasetMeta? = null,

        private var migrationKeySamples: Map<RecordMatchStatus, List<String>>? = null
    ) {
        fun matchStatus(matchStatus: MatchStatus?) = apply { this.matchStatus = matchStatus }
        fun sourceMeta(sourceMeta: DatasetMeta) = apply { this.sourceMeta = sourceMeta }
        fun targetMeta(targetMeta: DatasetMeta) = apply { this.targetMeta = targetMeta }
        fun migrationKeySamples(migrationKeySamples: Map<RecordMatchStatus, List<String>>) = apply { this.migrationKeySamples = migrationKeySamples }

        fun build() = matchStatus?.let {
            Summary(
                matchStatus!!.total,
                matchStatus!!.bothMatched,
                matchStatus!!.bothMismatched,
                source = IndividualDbResult(sourceMeta, matchStatus!!.sourceTotal, matchStatus!!.sourceOnly),
                target = IndividualDbResult(targetMeta, matchStatus!!.targetTotal, matchStatus!!.targetOnly),
            ).apply {
                migrationKeySamples?.let {
                    source.onlyHereSampleKeys = migrationKeySamples!![RecordMatchStatus.SourceOnly]
                    target.onlyHereSampleKeys = migrationKeySamples!![RecordMatchStatus.TargetOnly]
                    bothMismatchedSampleKeys = migrationKeySamples!![RecordMatchStatus.BothMismatched]
                }
            }
        }
    }
}

data class IndividualDbResult(
    val meta: DatasetMeta?,
    val totalCount: Int,
    val onlyHereCount: Int,
    var onlyHereSampleKeys: List<String>? = null,
)
