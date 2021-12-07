package recce.server.api

import io.micronaut.core.annotation.Introspected
import io.micronaut.http.annotation.*
import io.micronaut.validation.Validated
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

private const val maximumSampleKeys = 100L

@Validated
@Controller("/runs")
class DatasetRecRunController(
    @Inject private val runner: DatasetRecRunner,
    private val runRepository: RecRunRepository,
    private val recordRepository: RecRecordRepository
) {
    @Introspected
    data class RunQueryParams(
        @field:PathVariable val runId: Int,
        @field:QueryValue(defaultValue = "0") @field:PositiveOrZero @field:Max(maximumSampleKeys) val includeSampleKeys: Int = 0
    )

    @Suppress("MnUnresolvedPathVariable")
    @Get(uri = "/{runId}{?includeSampleKeys}")
    fun get(@Valid @RequestBean params: RunQueryParams): Mono<RunApiModel> {
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

    @Get
    fun get(@NotBlank @QueryValue("datasetId") datasetId: String): Flux<RunApiModel> {
        logger.info { "Find runs for [$datasetId]" }
        return runRepository.findTop10ByDatasetIdOrderByCompletedTimeDesc(datasetId)
            .map { RunApiModel.Builder(it).build() }
    }

    @Post
    fun create(@Body @Valid params: RunCreationParams): Mono<RunApiModel> {
        logger.info { "Received request to create run for $params" }
        return runner.runFor(params.datasetId).map { RunApiModel.Builder(it).build() }
    }

    @Introspected
    data class RunCreationParams(@field:NotBlank val datasetId: String)
}
