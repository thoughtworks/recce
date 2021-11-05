package recce.server

import io.micronaut.context.BeanLocator
import io.micronaut.context.annotation.ConfigurationInject
import io.micronaut.context.annotation.ConfigurationProperties
import recce.server.dataset.DatasetConfiguration
import javax.annotation.PostConstruct

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
