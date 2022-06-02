package recce.server.dataset

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
import kotlin.io.path.readText

class DataLoadDefinition
@ConfigurationInject constructor(
    @NotBlank val datasourceRef: String,
    val queryConfig: QueryConfig
) : PostConstructable {
    lateinit var dbOperations: R2dbcOperations
    lateinit var role: DataLoadRole
    lateinit var queryStatement: String

    override fun populate(locator: BeanLocator) {
        dbOperations = locator.findBean(R2dbcOperations::class.java, Qualifiers.byName(datasourceRef))
            .orElseThrow { ConfigurationException("Cannot locate ${R2dbcOperations::class.java.simpleName} named [$datasourceRef] in configuration!") }

        queryStatement = queryConfig.resolveQueryStatement()
    }

    companion object {
        const val migrationKeyColumnName: String = "MigrationKey"
    }

    fun runQuery(): Flux<Result> = Flux.usingWhen(
        dbOperations.connectionFactory().create(),
        { it.createStatement(queryStatement).execute() },
        { it.close() }
    )

    val datasourceDescriptor: String
        get() = "$role(ref=$datasourceRef)"
}

data class QueryConfig(
    val query: Optional<String> = Optional.empty(),
    val queryFile: Optional<Path> = Optional.empty()
) {
    init {
        require(!(this.query.isEmpty && this.queryFile.isEmpty)) { "query and queryFile cannot both be empty!" }
    }

    fun resolveQueryStatement(): String {
        return if (this.query.isEmpty) {
            if (this.queryFile.isEmpty) {
                throw ConfigurationException("query and queryFile cannot both be empty!")
            } else {
                try {
                    this.queryFile.get().readText(Charsets.UTF_8)
                } catch (e: Exception) {
                    throw ConfigurationException("Cannot load query statement from queryFile ${this.queryFile}: ${e.message}")
                }
            }
        } else {
            this.query.get()
        }
    }
}

enum class DataLoadRole { Source, Target }
