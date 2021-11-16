package recce.server

import io.micronaut.context.BeanLocator
import io.micronaut.context.annotation.ConfigurationInject
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Context
import io.micronaut.core.bind.annotation.Bindable
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
@ConfigurationInject constructor(
    val datasets: Map<String, DatasetConfiguration>,
    @Bindable(defaultValue = "1000") val defaultBatchSize: Int = 1000,
    @Bindable(defaultValue = "5") val defaultBatchConcurrency: Int = 5,
) : PostConstructable {

    @PostConstruct
    override fun populate(locator: BeanLocator) {
        for ((name, config) in datasets) {
            config.name = name
            config.populate(locator)
        }
        logger.info {
            "Loaded ${datasets.size} datasets available for triggering: " +
                "${datasets.values.groupBy { it.datasourceDescriptor }.toSortedMap()} "
        }
    }
}
