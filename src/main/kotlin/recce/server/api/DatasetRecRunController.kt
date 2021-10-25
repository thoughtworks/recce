package recce.server.api

import io.micronaut.core.annotation.Introspected
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.validation.Validated
import jakarta.inject.Inject
import mu.KotlinLogging
import reactor.core.publisher.Mono
import recce.server.dataset.DatasetRecRunner
import recce.server.dataset.RecRun
import javax.validation.Valid
import javax.validation.constraints.NotBlank

private val logger = KotlinLogging.logger {}

@Validated
@Controller("/runs")
class DatasetRecRunController(@Inject private val runner: DatasetRecRunner) {
    @Post
    fun create(@Body @Valid params: RunCreationParams): Mono<RecRun> {
        logger.info { "Received request to create run for $params" }
        return runner.runFor(params.datasetId)
    }

    @Introspected
    data class RunCreationParams(@field:NotBlank val datasetId: String)
}
