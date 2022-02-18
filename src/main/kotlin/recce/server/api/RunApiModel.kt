package recce.server.api

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import recce.server.recrun.*
import recce.server.util.ThrowableUtils.extractFailureCause
import java.time.Duration
import java.time.Instant

@Introspected
@Schema(
    name = "Run",
    description = "Results, timing and status of a pending or completed reconciliation run"
)
data class RunApiModel(
    @field:Schema(description = "Identifier of the individual reconciliation run")
    val id: Int,

    @field:Schema(description = "Identifier of the dataset that this reconciliation run is for")
    val datasetId: String,

    @field:Schema(description = "Time when the run started")
    val createdTime: Instant,

    @field:Schema(description = "Time when the run completed")
    val completedTime: Instant?,

    @field:Schema(description = "Status of this run")
    val status: RunStatus,

    @field:Schema(description = "Summary results from the run")
    val summary: RunSummary?,

    @field:Schema(description = "Reason for failure of the run")
    val failureCause: String? = null,

    @field: Schema(description = "Metadata about the run")
    val metadata: Map<String, String>
) {
    @get:Schema(type = "number", format = "double", description = "How long the run took, in seconds")
    @get:JsonProperty("completedDurationSeconds")
    val completedDuration: Duration?
        get() = if (completedTime != null) Duration.between(createdTime, completedTime) else null

    data class Builder(
        private val run: RecRun,
        private val summaryBuilder: RunSummary.Builder = RunSummary.Builder().matchStatus(run.summary)
    ) {
        fun migrationKeySamples(migrationKeySamples: Map<RecordMatchStatus, List<String>>) = apply { summaryBuilder.migrationKeySamples(migrationKeySamples) }

        fun build() = RunApiModel(
            id = run.id!!,
            datasetId = run.datasetId,
            createdTime = run.createdTime!!,
            completedTime = run.completedTime,
            status = run.status,
            summary = summaryBuilder
                .sourceMeta(run.sourceMeta)
                .targetMeta(run.targetMeta)
                .build(),
            failureCause = run.failureCause?.run { extractFailureCause(this) },
            metadata = run.metadata
        )
    }
}

@Schema(
    name = "RunSummary",
    description = "Summarised matching results and metadata for a completed reconciliation run"
)
data class RunSummary(
    @field:Schema(description = "Total number of rows in the dataset across both datasources, determined by distinct MigrationKey values")
    val totalCount: Int,

    @field:Schema(description = "Number of rows considered matched (identical values) across both source and target")
    val bothMatchedCount: Int,

    @field:Schema(description = "Number of rows considered mis-matched (one or more column value differed) across both source and target")
    val bothMismatchedCount: Int,

    @field:Schema(description = "Sample MigrationKeys for rows considered mis-matched that may need deeper investigation")
    var bothMismatchedSampleKeys: List<String>? = null,

    @field:Schema(description = "Summary results relating only to the dataset from the source datasource")
    val source: IndividualDbRunSummary,

    @field:Schema(description = "Summary results relating only to the dataset from the target datasource")
    val target: IndividualDbRunSummary
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
            RunSummary(
                matchStatus!!.total,
                matchStatus!!.bothMatched,
                matchStatus!!.bothMismatched,
                source = IndividualDbRunSummary(IndividualDbMeta(sourceMeta), matchStatus!!.sourceTotal, matchStatus!!.sourceOnly),
                target = IndividualDbRunSummary(IndividualDbMeta(targetMeta), matchStatus!!.targetTotal, matchStatus!!.targetOnly),
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

@Schema(
    name = "IndividualDatabaseRunSummary",
    description = "Summarised results relating to only the source or target database, but not the other"
)
data class IndividualDbRunSummary(
    @field:Schema(description = "Metadata relating to the query executed on the underlying datasource")
    val meta: IndividualDbMeta?,

    @field:Schema(description = "Total number of rows in the dataset for an individual datasource")
    val totalCount: Int,

    @field:Schema(description = "Number of rows only found in this datasource")
    val onlyHereCount: Int,

    @field:Schema(description = "Sample MigrationKeys for rows only found within this datasource")
    var onlyHereSampleKeys: List<String>? = null,
)

@Schema(name = "IndividualDatabaseMetadata", description = "Metadata about a single dataset when run against a given data source")
data class IndividualDbMeta(
    @field:Schema(description = "Metadata describing the individual columns within the dataset query, ordered as in the query expression")
    var cols: List<ColMeta> = emptyList()
) {
    internal constructor(meta: DatasetMeta?) : this(meta?.cols?.map { ColMeta(it) } ?: emptyList())
}

@Schema(
    name = "ColumnMetadata",
    description = "Metadata about a single column as retrieved from a dataset query"
)
data class ColMeta(
    @field:Schema(description = "Name of the column as retrieved by the dataset query. Name columns in your dataset SQL expressions to alter these.")
    val name: String,

    @field:Schema(description = "The deserialized Java type representing the column after retrieval from the data DataLoadRole.source. This can help you understand why two rows may not have a matching hash. If the columns have incompatible types which will not be hashed consistently, you may need to coerce the types in your dataset query. ")
    val javaType: String
) {
    internal constructor(meta: recce.server.recrun.ColMeta) : this(meta.name, meta.javaType)
}
