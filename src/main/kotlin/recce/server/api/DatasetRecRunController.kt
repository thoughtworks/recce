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
import reactor.kotlin.core.util.function.component3
import reactor.kotlin.core.util.function.component4
import recce.server.dataset.DatasetRecRunner
import recce.server.recrun.RecRecordRepository
import recce.server.recrun.RecRunRepository
import javax.validation.Valid
import javax.validation.constraints.NotBlank

private val logger = KotlinLogging.logger {}

@Validated
@Controller("/runs")
class DatasetRecRunController(
    @Inject private val runner: DatasetRecRunner,
    private val runRepository: RecRunRepository,
    private val recordRepository: RecRecordRepository
) {
    @Get(uri = "/{runId}")
    fun get(runId: Int): Mono<RunApiModel> {
        logger.info { "Finding run [$runId]" }
        return Mono.zip(
            runRepository.findById(runId),
            recordRepository.findFirst10ByRecRunIdAndTargetDataIsNull(runId).map { it.migrationKey }.collectList().defaultIfEmpty(emptyList()),
            recordRepository.findFirst10ByRecRunIdAndSourceDataIsNull(runId).map { it.migrationKey }.collectList().defaultIfEmpty(emptyList()),
            recordRepository.findFirst10ByRecRunIdAndSourceDataNotEqualsTargetData(runId).map { it.migrationKey }.collectList().defaultIfEmpty(emptyList())
        ).map { (run, sourceOnlySampleRows, targetOnlySampleRows, mismatchedSampleRows) ->
            RunApiModel
                .Builder(run)
                .migrationKeySamples(mapOf("source" to sourceOnlySampleRows, "target" to targetOnlySampleRows, "both" to mismatchedSampleRows))
                .build()
        }
    }

    @Get
    fun get(@NotBlank @QueryValue("datasetId") datasetId: String): Flux<RunApiModel> {
        logger.info { "Find runs for [$datasetId]" }
        return runRepository.findTop10ByDatasetIdOrderByCompletedTimeDesc(datasetId).map { RunApiModel.Builder(it).build() }
    }

    @Post
    fun create(@Body @Valid params: RunCreationParams): Mono<RunApiModel> {
        logger.info { "Received request to create run for $params" }
        return runner.runFor(params.datasetId).map { RunApiModel.Builder(it).build() }
    }

    @Introspected
    data class RunCreationParams(@field:NotBlank val datasetId: String)
}
