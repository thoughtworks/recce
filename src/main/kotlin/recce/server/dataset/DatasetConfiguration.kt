package recce.server.dataset

import io.micronaut.context.BeanLocator
import io.micronaut.core.bind.annotation.Bindable
import io.micronaut.scheduling.cron.CronExpression
import recce.server.PostConstructable
import java.time.ZonedDateTime
import javax.validation.constraints.NotNull

class DatasetConfiguration(
    @NotNull val source: DataLoadDefinition,
    @NotNull val target: DataLoadDefinition,
    @Bindable(defaultValue = "") val schedule: Schedule = Schedule()
) :
    PostConstructable {
    lateinit var name: String
    override fun populate(locator: BeanLocator) {
        source.role = DataLoadRole.source
        source.populate(locator)
        target.role = DataLoadRole.target
        target.populate(locator)
    }

    val datasourceDescriptor: String
        get() = "(${source.dataSourceRef} -> ${target.dataSourceRef})"

    override fun toString(): String {
        return name
    }
}

data class Schedule(val cronExpression: String? = null) {
    val summary: String
        get() = if (cronExpression == null) "" else "[$cronExpression], next run [${CronExpression.create(cronExpression).nextTimeAfter(ZonedDateTime.now())}]"
}
