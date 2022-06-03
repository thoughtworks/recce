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
    lateinit var queryFileBaseDir: Optional<Path>

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
        .index()
        .map { (i, r) -> if (i > 0) throw IllegalArgumentException("More than one query found.") else r }

    @VisibleForTesting
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

    val datasourceDescriptor: String
        get() = "$role(ref=$datasourceRef)"
}

enum class DataLoadRole { Source, Target }
