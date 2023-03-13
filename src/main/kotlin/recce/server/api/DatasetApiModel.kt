package recce.server.api

import io.swagger.v3.oas.annotations.media.Schema
import recce.server.dataset.DatasetConfiguration
import recce.server.dataset.Schedule
import java.time.ZonedDateTime

@Schema(name = "Dataset", description = "Configuration for a dataset that is available for triggering")
data class DatasetApiModel(
    @field:Schema(description = "Identifier for this dataset as specified in server configuration")
    val id: String,

    @field:Schema(description = "Datasource considered the `source` of this reconciliation dataset")
    val source: DatasourceApiModel,

    @field:Schema(description = "Datasource considered the `target` of this reconciliation dataset")
    val target: DatasourceApiModel,

    @field:Schema(description = "Information about the scheduling of this dataset, if configured")
    val schedule: ScheduleApiModel? = null
) {
    constructor(config: DatasetConfiguration) : this(
        config.id,
        DatasourceApiModel(config.source.datasourceRef),
        DatasourceApiModel(config.target.datasourceRef),
        if (config.schedule.empty) null else ScheduleApiModel(config.schedule)
    )
}

@Schema(name = "Datasource")
data class DatasourceApiModel(

    @field:Schema(description = "The logical name of the datasource this refers to")
    val ref: String
)

@Schema(name = "Schedule", description = "Scheduling information")
data class ScheduleApiModel(
    @field:Schema(
        description = "Cron Expression valid per " +
            "https://docs.micronaut.io/latest/api/io/micronaut/scheduling/cron/CronExpression.html"
    )
    val cronExpression: String,

    @field:Schema(description = "Time this dataset is scheduled to next be triggered")
    val nextTriggerTime: ZonedDateTime
) {
    constructor(schedule: Schedule) : this(schedule.cronExpression!!, schedule.nextTriggerTime)
}
