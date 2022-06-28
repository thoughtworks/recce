package recce.server.dataset

import com.google.common.annotations.VisibleForTesting
import io.micronaut.context.BeanLocator
import io.micronaut.context.annotation.ConfigurationInject
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.data.r2dbc.operations.R2dbcOperations
import io.micronaut.inject.qualifiers.Qualifiers
import io.r2dbc.spi.Result
import reactor.core.publisher.Flux
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2
import recce.server.DefaultsProvider
import recce.server.PostConstructable
import java.nio.file.Path
import java.util.*
import javax.validation.constraints.NotBlank
import kotlin.io.path.readText

class DataLoadDefinition
@ConfigurationInject constructor(
    @NotBlank val datasourceRef: String,
    var query: Optional<String> = Optional.empty(),
    var queryFile: Optional<Path> = Optional.empty()
) : PostConstructable {
    lateinit var dbOperations: R2dbcOperations
    lateinit var role: DataLoadRole
    lateinit var queryStatement: String
    lateinit var queryFileBaseDir: Path
    lateinit var datasetId: String

    override fun populate(locator: BeanLocator) {
        dbOperations = locator.findBean(R2dbcOperations::class.java, Qualifiers.byName(datasourceRef))
            .orElseThrow { ConfigurationException("Cannot locate ${R2dbcOperations::class.java.simpleName} named [$datasourceRef] in configuration!") }

        queryFileBaseDir = locator.findBean(DefaultsProvider::class.java)
            .orElseThrow { ConfigurationException("Cannot locate ${DefaultsProvider::class.java.simpleName} in configuration!") }
            .queryFileBaseDir

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
        .index()
        .map { (i, r) -> require(i == 0L) { "More than one query found." }; r }

    @VisibleForTesting
    fun resolveQueryStatement(): String = kotlin.runCatching {
        this.query.orElseGet {
            this.queryFile.orElseGet {
                this.queryFileBaseDir.resolve("${this.datasetId}-${this.role.name.lowercase()}.sql")
            }.readText(Charsets.UTF_8)
        }
    }
        .onFailure { e -> throw ConfigurationException("Cannot load query: ${e.message}") }
        .getOrThrow()

    val datasourceDescriptor: String
        get() = "$role(ref=$datasourceRef)"
}

enum class DataLoadRole { Source, Target }
