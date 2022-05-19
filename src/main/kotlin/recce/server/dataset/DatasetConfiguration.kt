package recce.server.dataset

import com.google.common.annotations.VisibleForTesting
import io.micronaut.context.BeanLocator
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.core.annotation.Nullable
import io.micronaut.core.bind.annotation.Bindable
import io.micronaut.scheduling.cron.CronExpression
import recce.server.DefaultsProvider
import recce.server.PostConstructable
import java.time.ZonedDateTime
import java.util.*
import javax.validation.constraints.NotNull

class DatasetConfiguration(
    @NotNull val source: DataLoadDefinition,
    @NotNull val target: DataLoadDefinition,
    @Bindable(defaultValue = "") val schedule: Schedule = Schedule(),
    @Nullable val hashingStrategy: Optional<HashingStrategy> = Optional.empty()
) : PostConstructable {

    lateinit var id: String
    lateinit var defaults: DefaultsProvider

    @VisibleForTesting
    constructor(source: DataLoadDefinition, target: DataLoadDefinition) : this(
        source,
        target,
        Schedule(),
        Optional.empty()
    ) {
        defaults = DefaultsProvider()
    }

    override fun populate(locator: BeanLocator) {
        defaults = locator.getBean(DefaultsProvider::class.java)
        source.role = DataLoadRole.Source
        source.populate(locator)
        target.role = DataLoadRole.Target
        target.populate(locator)
    }

    override fun toString(): String {
        return id
    }

    val datasourceDescriptor: String
        get() = "(${source.datasourceRef} -> ${target.datasourceRef})"

    val resolvedHashingStrategy: HashingStrategy
        get() = hashingStrategy.orElseGet { defaults.hashingStrategy }
}

data class Schedule(val cronExpression: String? = null, val deleteRunsOlderThanISOString: String? = null) {
    init {
        if (deleteRunsOlderThanISOString != null) requireNotNull(cronExpression) {
            throw ConfigurationException("Older runs can only be deleted for datasets on cron schedule")
        }
    }

    val empty: Boolean
        get() = cronExpression == null

    val nextTriggerTime: ZonedDateTime
        get() = CronExpression.create(cronExpression).nextTimeAfter(ZonedDateTime.now())

    val deleteRunsOlderThan: ZonedDateTime
        get() = ZonedDateTime.parse(deleteRunsOlderThanISOString)

    val summary: String
        get() = if (empty) "" else "[$cronExpression], next run [$nextTriggerTime], if successful, will cleanup runs older than [$deleteRunsOlderThanISOString]"
}
