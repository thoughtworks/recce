package recce.server.config

import io.micronaut.context.BeanLocator
import io.micronaut.context.annotation.ConfigurationInject
import io.micronaut.context.annotation.ConfigurationProperties
import javax.annotation.PostConstruct
import javax.validation.constraints.NotNull

interface PostConstructable {
    fun populate(locator: BeanLocator)
}

@ConfigurationProperties("reconciliation")
class RecConfiguration
@ConfigurationInject constructor(val datasets: Map<String, DatasetConfiguration>) : PostConstructable {

    @PostConstruct
    override fun populate(locator: BeanLocator) {
        for ((name, config) in datasets) {
            config.name = name
            config.populate(locator)
        }
    }
}

class DatasetConfiguration(@NotNull val source: DataLoadDefinition, @NotNull val target: DataLoadDefinition) :
    PostConstructable {
    lateinit var name: String
    override fun populate(locator: BeanLocator) {
        source.populate(locator)
        target.populate(locator)
    }
}
