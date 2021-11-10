package recce.server.api

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.annotation.*
import io.micronaut.validation.Validated
import jakarta.inject.Inject
import mu.KotlinLogging
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import recce.server.dataset.DatasetRecRunner
import recce.server.recrun.DatasetMeta
import recce.server.recrun.MatchStatus
import recce.server.recrun.RecRun
import recce.server.recrun.RecRunRepository
import java.time.Duration
import java.time.Instant
import javax.validation.Valid
import javax.validation.constraints.NotBlank

private val logger = KotlinLogging.logger {}

@Validated
@Controller("/runs")
class DatasetRecRunController(
    @Inject private val runner: DatasetRecRunner,
    private val runRepository: RecRunRepository
) {
    @Get(uri = "/{runId}")
    fun get(runId: Int): Mono<RunApiModel> {
        logger.info { "Finding run [$runId]" }
        return runRepository.findById(runId).map { RunApiModel(it) }
    }

    @Get
    fun get(@NotBlank @QueryValue("datasetId") datasetId: String): Flux<RunApiModel> {
        logger.info { "Find runs for [$datasetId]" }
        return runRepository.findTop10ByDatasetIdOrderByCompletedTimeDesc(datasetId).map { RunApiModel(it) }
    }

    @Post
    fun create(@Body @Valid params: RunCreationParams): Mono<RunApiModel> {
        logger.info { "Received request to create run for $params" }
        return runner.runFor(params.datasetId).map { RunApiModel(it) }
    }

    @Introspected
    data class RunCreationParams(@field:NotBlank val datasetId: String)

    @Introspected
    data class RunApiModel(
        val id: Int,
        val datasetId: String,
        val createdTime: Instant,
        val completedTime: Instant?,
        val sourceMeta: DatasetMeta,
        val targetMeta: DatasetMeta,
        val summary: MatchStatus?
    ) {
        constructor(run: RecRun) : this(
            id = run.id!!,
            datasetId = run.datasetId,
            createdTime = run.createdTime!!,
            completedTime = run.completedTime,
            sourceMeta = run.sourceMeta,
            targetMeta = run.targetMeta,
            summary = run.summary
        )

        @get:JsonProperty("completedDurationSeconds")
        val completedDuration: Duration?
            get() = if (completedTime != null) Duration.between(createdTime, completedTime) else null
    }
}
