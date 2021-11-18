package recce.server.dataset

import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.runtime.server.event.ServerStartupEvent
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.TaskScheduler
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import mu.KotlinLogging
import recce.server.RecConfiguration

private val logger = KotlinLogging.logger {}

@Singleton
class DatasetRecScheduler(
    @Inject private val config: RecConfiguration,
    private val runner: DatasetRecRunner,
    @param:Named(TaskExecutors.SCHEDULED) private val scheduler: TaskScheduler
) : ApplicationEventListener<ServerStartupEvent> {

    override fun onApplicationEvent(event: ServerStartupEvent?) {
        logger.info { "Scheduling regular recs..." }
        config.datasets.values.forEach(::schedule)
    }

    private fun schedule(config: DatasetConfiguration) {
        config.schedule.cronExpression?.let { expr ->
            try {
                logger.info { "Scheduling rec for [${config.name}] with cron ${config.schedule.summary}" }
                scheduler.schedule(expr) {
                    logger.info { "Triggering scheduled reconciliation of [${config.name}]" }
                    runner.runFor(config.name).subscribe()
                }
            } catch (e: Exception) {
                throw ConfigurationException("Schedule for [${config.name}] is invalid: ${e.message}", e)
            }
        }
    }
}
