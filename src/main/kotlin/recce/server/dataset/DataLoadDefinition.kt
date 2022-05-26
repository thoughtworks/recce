package recce.server.dataset

import io.micronaut.context.BeanLocator
import io.micronaut.context.annotation.ConfigurationInject
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.data.r2dbc.operations.R2dbcOperations
import io.micronaut.inject.qualifiers.Qualifiers
import io.r2dbc.spi.Result
import reactor.core.publisher.Flux
import recce.server.PostConstructable
import java.io.File
import javax.validation.constraints.NotBlank

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

data class QueryConfig(val query: String = "", private val queryFile: String = "") {
    init {
        require(!(this.query.isEmpty() && this.queryFile.isEmpty())) { "query and queryFile cannot both be empty!" }
    }

    fun resolveQueryStatement(): String {
        return this.query.ifEmpty {
            try {
                File(this.queryFile).readText(Charsets.UTF_8)
            } catch (e: Exception) {
                throw ConfigurationException("Cannot load query statement from queryFile $this.queryFile: ${e.message}")
            }
        }
    }
}

enum class DataLoadRole { Source, Target }
