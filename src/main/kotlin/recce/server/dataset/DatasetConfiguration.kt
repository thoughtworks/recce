package recce.server.dataset

import com.google.common.annotations.VisibleForTesting
import io.micronaut.context.BeanLocator
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

    lateinit var name: String
    lateinit var defaults: DefaultsProvider

    @VisibleForTesting
    constructor(source: DataLoadDefinition, target: DataLoadDefinition) : this(source, target, Schedule(), Optional.empty()) {
        defaults = DefaultsProvider()
    }

    override fun populate(locator: BeanLocator) {
        defaults = locator.getBean(DefaultsProvider::class.java)
        source.role = DataLoadRole.source
        source.populate(locator)
        target.role = DataLoadRole.target
        target.populate(locator)
    }

    override fun toString(): String {
        return name
    }

    val datasourceDescriptor: String
        get() = "(${source.dataSourceRef} -> ${target.dataSourceRef})"

    val resolvedHashingStrategy: HashingStrategy
        get() = hashingStrategy.orElseGet { defaults.hashingStrategy }
}

data class Schedule(val cronExpression: String? = null) {
    val summary: String
        get() = if (cronExpression == null) "" else "[$cronExpression], next run [${CronExpression.create(cronExpression).nextTimeAfter(ZonedDateTime.now())}]"
}
