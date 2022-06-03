package recce.server

import io.micronaut.context.BeanLocator
import io.micronaut.context.annotation.ConfigurationInject
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Context
import io.micronaut.core.bind.annotation.Bindable
import mu.KotlinLogging
import recce.server.dataset.DatasetConfiguration
import recce.server.dataset.HashingStrategy
import java.nio.file.Path
import java.util.*
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
    val defaults: DefaultsProvider = DefaultsProvider(),
    val queryFileBaseDir: Optional<Path> = Optional.empty()
) : PostConstructable {

    @PostConstruct
    override fun populate(locator: BeanLocator) {
        for ((id, config) in datasets) {
            config.id = id
            config.queryFileBaseDir = queryFileBaseDir
            config.populate(locator)
        }
        logger.info {
            "Loaded ${datasets.size} datasets available for triggering: " +
                "${datasets.values.groupBy { it.datasourceDescriptor }.toSortedMap()} "
        }
    }
}

@Context
@ConfigurationProperties("reconciliation.defaults")
class DefaultsProvider @ConfigurationInject constructor(
    @Bindable(defaultValue = "1000") val batchSize: Int,
    @Bindable(defaultValue = "5") val batchConcurrency: Int,
    @Bindable(defaultValue = "TypeLenient") val hashingStrategy: HashingStrategy
) {
    constructor() : this(
        batchSize = 1000,
        batchConcurrency = 5,
        hashingStrategy = HashingStrategy.TypeLenient
    )
}
