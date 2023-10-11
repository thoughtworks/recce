package recce.server.api

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.micronaut.validation.Validated
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject
import recce.server.dataset.DatasetConfigProvider

@Validated
@Controller("/datasets")
@Secured(SecurityRule.IS_AUTHENTICATED)
class DatasetController(
    @Inject private val configProvider: DatasetConfigProvider
) {
    @Get
    @Operation(
        summary = "Retrieve pre-configured datasets",
        description = "Retrieves all available datasets that have been pre-configured and loaded by the server",
        tags = ["Datasets"],
        responses = [ApiResponse(responseCode = "200", description = "Dataset configurations found")]
    )
    fun getDatasets() =
        configProvider.availableDataSets
            .map { DatasetApiModel(it) }
            .sortedBy { it.id }
}
