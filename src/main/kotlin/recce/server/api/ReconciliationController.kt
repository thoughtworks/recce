package recce.server.api

import io.micronaut.core.annotation.Introspected
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.validation.Validated
import jakarta.inject.Inject
import mu.KotlinLogging
import reactor.core.publisher.Mono
import recce.server.dataset.MigrationRun
import recce.server.dataset.ReconciliationRunner
import javax.validation.Valid
import javax.validation.constraints.NotBlank

private val logger = KotlinLogging.logger {}

@Validated
@Controller("/runs")
class ReconciliationController(@Inject private val service: ReconciliationRunner) {
    @Post
    fun create(@Body @Valid params: RunCreationParams): Mono<MigrationRun> {
        logger.info { "Received request to create run for $params" }
        return service.runFor(params.datasetId)
    }

    @Introspected
    data class RunCreationParams(@field:NotBlank val datasetId: String)
}
