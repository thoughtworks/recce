package recce.server.api

import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.annotation.*
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.micronaut.validation.Validated
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject
import mu.KotlinLogging
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2
import recce.server.dataset.DatasetRecRunner
import recce.server.recrun.RecRecordRepository
import recce.server.recrun.RecRunRepository
import javax.validation.Valid
import javax.validation.constraints.Max
import javax.validation.constraints.NotBlank
import javax.validation.constraints.PositiveOrZero

private val logger = KotlinLogging.logger {}

private const val MAXIMUM_SAMPLE_KEYS = 100L

@Validated
@Controller("/runs")
@Secured(SecurityRule.IS_AUTHENTICATED)
class DatasetRecRunController(
    @Inject private val runner: DatasetRecRunner,
    private val runRepository: RecRunRepository,
    private val recordRepository: RecRecordRepository
) {
    @Introspected
    data class IndividualRunQueryParams(
        @field:Schema(description = "The identifier of the reconciliation run to retrieve")
        @field:PathVariable
        val runId: Int,

        @field:Schema(description = "How many sample mismatched migration keys of each type (only in source, only in target, mismatched data) in the results")
        @field:QueryValue(defaultValue = "0")
        @field:Nullable
        @field:PositiveOrZero
        @field:Max(MAXIMUM_SAMPLE_KEYS)
        val includeSampleKeys: Int = 0
    )

    @Get(uri = "/{runId}{?includeSampleKeys}")
    @Suppress("MnUnresolvedPathVariable")
    @Operation(
        summary = "Retrieve details of an individual run by ID for a dataset",
        tags = ["Reconciliation Runs"],
        responses = [ApiResponse(responseCode = "200", description = "Reconciliation run found")]
    )
    fun retrieveIndividualRun(
        @Valid @RequestBean
        params: IndividualRunQueryParams
    ): Mono<RunApiModel> {
        logger.info { "Finding $params" }

        val findSampleRows = if (params.includeSampleKeys == 0) {
            Mono.just(emptyMap())
        } else {
            recordRepository.findFirstByRecRunIdSplitByMatchStatus(params.runId, params.includeSampleKeys)
                .map { it.matchStatus to it.migrationKey }
                .collectList()
                .defaultIfEmpty(emptyList())
                .map { records ->
                    records.groupBy { it.first }
                        .mapValues { entry -> entry.value.map { pair -> pair.second } }
                }
        }

        return runRepository
            .findById(params.runId)
            .zipWith(findSampleRows)
            .map { (run, mismatchedRows) ->
                RunApiModel
                    .Builder(run)
                    .migrationKeySamples(mismatchedRows)
                    .build()
            }
    }

    @Introspected
    data class RunQueryParams(
        @field:Schema(description = "Identifier of the dataset to search for runs for")
        @field:NotBlank
        @field:QueryValue("datasetId")
        val datasetId: String
    )

    @Get
    @Operation(
        summary = "Retrieve details of recent runs for a dataset",
        description = "Gets the last 10 runs (whether completed or not) for a given dataset.",
        tags = ["Reconciliation Runs"],
        responses = [ApiResponse(responseCode = "200", description = "Dataset found")]
    )
    fun retrieveRuns(
        @Valid @RequestBean
        params: RunQueryParams
    ): Flux<RunApiModel> {
        logger.info { "Finding runs for $params" }
        return runRepository.findTop10ByDatasetIdOrderByCompletedTimeDesc(params.datasetId)
            .map { RunApiModel.Builder(it).build() }
    }

    @Post
    @Operation(
        summary = "Trigger a reconciliation run for a dataset",
        description = "Synchronously triggers a reconciliation run for a dataset; returning only on its completion.",
        tags = ["Reconciliation Runs"],
        responses = [ApiResponse(responseCode = "200", description = "Run triggered for dataset")]
    )
    fun triggerRun(
        @Body @Valid
        params: RunCreationParams
    ): Mono<RunApiModel> {
        logger.info { "Received request to create run for $params" }
        return runner.runFor(params.datasetId).map { RunApiModel.Builder(it).build() }
    }

    @Introspected
    @Schema(name = "RunCreationParams", description = "Control how a new reconciliation run is created")
    data class RunCreationParams(
        @field:Schema(description = "Identifier of the dataset to create a new run for")
        @field:NotBlank
        val datasetId: String
    )
}
