package recce.server.dataset

import io.micronaut.context.BeanLocator
import io.micronaut.core.annotation.Nullable
import io.micronaut.core.bind.annotation.Bindable
import io.micronaut.scheduling.cron.CronExpression
import org.jetbrains.annotations.TestOnly
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

    @TestOnly
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
        source.datasetId = id
        source.role = DataLoadRole.Source
        source.populate(locator)
        target.datasetId = id
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

data class Schedule(val cronExpression: String? = null) {
    val empty: Boolean
        get() = cronExpression == null

    val nextTriggerTime: ZonedDateTime
        get() = CronExpression.create(cronExpression).nextTimeAfter(ZonedDateTime.now())

    val summary: String
        get() = if (empty) "" else "[$cronExpression], next run [$nextTriggerTime]"
}
