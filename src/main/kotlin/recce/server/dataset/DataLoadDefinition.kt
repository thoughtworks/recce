package recce.server.dataset

import com.google.common.annotations.VisibleForTesting
import io.micronaut.context.BeanLocator
import io.micronaut.context.annotation.ConfigurationInject
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.data.r2dbc.operations.R2dbcOperations
import io.micronaut.inject.qualifiers.Qualifiers
import io.r2dbc.spi.Result
import reactor.core.publisher.Flux
import recce.server.PostConstructable
import java.nio.file.Path
import java.util.*
import javax.validation.constraints.NotBlank
import kotlin.io.path.Path
import kotlin.io.path.readText

object DataLoadDefinitionConstants {
    const val DEFAULT_QUERY_FILE_BASE_DIR = "queries"
}

class DataLoadDefinition
@ConfigurationInject constructor(
    @NotBlank val datasourceRef: String,
    var query: Optional<String> = Optional.empty(),
    var queryFile: Optional<Path> = Optional.empty()
) : PostConstructable {
    lateinit var dbOperations: R2dbcOperations
    lateinit var role: DataLoadRole
    lateinit var queryStatement: String
    lateinit var queryFileBaseDir: Optional<Path>
    lateinit var datasetId: String

    override fun populate(locator: BeanLocator) {
        dbOperations = locator.findBean(R2dbcOperations::class.java, Qualifiers.byName(datasourceRef))
            .orElseThrow { ConfigurationException("Cannot locate ${R2dbcOperations::class.java.simpleName} named [$datasourceRef] in configuration!") }

        queryStatement = this.resolveQueryStatement()
    }

    companion object {
        const val migrationKeyColumnName: String = "MigrationKey"
    }

    fun runQuery(): Flux<Result> = Flux.usingWhen(
        dbOperations.connectionFactory().create(),
        { it.createStatement(queryStatement).execute() },
        { it.close() }
    )

    @VisibleForTesting
    fun resolveQueryStatement(): String {
        try {
            return if (this.query.isEmpty) {
                if (this.queryFile.isEmpty) {
                    if (this.queryFileBaseDir.isEmpty) {
                        Path(buildQueryFilePath(Path(DataLoadDefinitionConstants.DEFAULT_QUERY_FILE_BASE_DIR))).readText(Charsets.UTF_8)
                    } else {
                        Path(buildQueryFilePath(this.queryFileBaseDir.get())).readText(Charsets.UTF_8)
                    }
                } else {
                    this.queryFile.get().readText(Charsets.UTF_8)
                }
            } else {
                this.query.get()
            }
        } catch (e: Exception) {
            throw ConfigurationException("Cannot load query: ${e.message}")
        }
    }

    private fun buildQueryFilePath(dir: Path): String {
        return "$dir/${this.datasetId}-${this.role.name.lowercase()}.sql"
    }

    val datasourceDescriptor: String
        get() = "$role(ref=$datasourceRef)"
}

enum class DataLoadRole { Source, Target }
