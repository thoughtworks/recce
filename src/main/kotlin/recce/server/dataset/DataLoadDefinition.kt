package recce.server.dataset

import io.micronaut.context.BeanLocator
import io.micronaut.context.annotation.ConfigurationInject
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.data.r2dbc.operations.R2dbcOperations
import io.micronaut.inject.qualifiers.Qualifiers
import io.r2dbc.spi.Result
import org.jetbrains.annotations.TestOnly
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
    @ConfigurationInject
    constructor(
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
            val ops = R2dbcOperations::class.java
            dbOperations =
                locator.findBean(ops, Qualifiers.byName(datasourceRef))
                    .orElseThrow {
                        ConfigurationException(
                            "Cannot locate ${ops.simpleName} named [$datasourceRef] in configuration!"
                        )
                    }

            val defaults = DefaultsProvider::class.java
            queryFileBaseDir =
                locator.findBean(defaults)
                    .orElseThrow {
                        ConfigurationException("Cannot locate ${defaults.simpleName} in configuration!")
                    }
                    .queryFileBaseDir

            queryStatement = this.resolveQueryStatement()
        }

        companion object {
            const val MIGRATION_KEY_COLUMN_NAME = "MigrationKey"
        }

        fun runQuery(): Flux<Result> =
            Flux.usingWhen(
                dbOperations.connectionFactory().create(),
                { it.createStatement(queryStatement).execute() },
                { it.close() }
            )
                .index()
                .map { (i, r) ->
                    require(i == 0L) { "More than one query found." }
                    r
                }

        @TestOnly
        fun resolveQueryStatement(): String =
            kotlin.runCatching {
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
