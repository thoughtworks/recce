package recce.server.config

import io.micronaut.context.BeanLocator
import io.micronaut.context.annotation.ConfigurationInject
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.core.bind.annotation.Bindable
import io.micronaut.data.r2dbc.operations.R2dbcOperations
import io.micronaut.inject.qualifiers.Qualifiers
import javax.annotation.PostConstruct
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

interface PostConstructable {
    fun populate(locator: BeanLocator)
}

@ConfigurationProperties("reconciliation")
class ReconciliationConfiguration
@ConfigurationInject constructor(
    @Bindable(defaultValue = "") val triggerOnStart: List<String> = emptyList(),
    val datasets: Map<String, DataSetConfiguration>
) : PostConstructable {

    @PostConstruct
    override fun populate(locator: BeanLocator) {
        for ((name, config) in datasets) {
            config.name = name
            config.populate(locator)
        }
    }
}

class DataSetConfiguration(@NotNull val source: DataLoadDefinition, @NotNull val target: DataLoadDefinition) :
    PostConstructable {
    lateinit var name: String
    override fun populate(locator: BeanLocator) {
        source.populate(locator)
        target.populate(locator)
    }
}

class DataLoadDefinition(@NotBlank val dataSourceRef: String, @NotBlank val query: String) : PostConstructable {
    lateinit var dbOperations: R2dbcOperations

    override fun populate(locator: BeanLocator) {
        dbOperations = locator.findBean(R2dbcOperations::class.java, Qualifiers.byName(dataSourceRef))
            .orElseThrow { ConfigurationException("Cannot locate ${R2dbcOperations::class.java.simpleName} named [$dataSourceRef] in configuration!") }
    }

    companion object {
        const val migrationKeyColumnName: String = "MigrationKey"
    }
}
