package recce.server

import io.micronaut.context.BeanLocator
import io.micronaut.context.annotation.ConfigurationInject
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Context
import mu.KotlinLogging
import recce.server.dataset.DatasetConfiguration
import javax.annotation.PostConstruct

private val logger = KotlinLogging.logger {}

interface PostConstructable {
    fun populate(locator: BeanLocator)
}

@Context
@ConfigurationProperties("reconciliation")
class RecConfiguration
@ConfigurationInject constructor(val datasets: Map<String, DatasetConfiguration>) : PostConstructable {

    @PostConstruct
    override fun populate(locator: BeanLocator) {
        for ((name, config) in datasets) {
            config.name = name
            config.populate(locator)
        }
        logger.info { "Loaded ${datasets.size} datasets available for triggering: ${datasets.values.map { it.shortDescriptor }} " }
    }
}
