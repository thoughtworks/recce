package recce.server.recrun

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.micronaut.core.convert.ConversionContext
import io.micronaut.data.annotation.DateCreated
import io.micronaut.data.annotation.DateUpdated
import io.micronaut.data.annotation.TypeDef
import io.micronaut.data.model.DataType
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.runtime.convert.AttributeConverter
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import java.time.Instant
import javax.persistence.*

@R2dbcRepository(dialect = Dialect.POSTGRES)
interface RecRunRepository : ReactorCrudRepository<RecRun, Int> {
    fun findTop10ByDatasetIdOrderByCompletedTimeDesc(datasetId: String): Flux<RecRun>
}

@Entity
@Table(name = "reconciliation_run")
data class RecRun(
    @Id @GeneratedValue val id: Int? = null,
    val datasetId: String,
    @DateCreated val createdTime: Instant? = null,
    @DateUpdated var updatedTime: Instant? = null,
    var completedTime: Instant? = null,
    @Enumerated(EnumType.STRING) var status: RunStatus = RunStatus.Pending,
    @Embedded var summary: MatchStatus? = null
) {
    constructor(datasetId: String) : this(null, datasetId)

    @Embedded var sourceMeta: DatasetMeta = DatasetMeta()
    @Embedded var targetMeta: DatasetMeta = DatasetMeta()

    @Transient var failureCause: Throwable? = null

    @Suppress("JpaAttributeTypeInspection") // False positive
    @field:TypeDef(type = DataType.JSON)
    var metadata: Map<String, String> = emptyMap()

    fun withMetaData(source: DatasetMeta, target: DatasetMeta): RecRun {
        sourceMeta = source
        targetMeta = target
        return this
    }

    fun asSuccessful(summary: MatchStatus): RecRun {
        this.summary = summary
        completedTime = Instant.now()
        status = RunStatus.Successful
        return this
    }

    fun asFailed(cause: Throwable): RecRun {
        this.failureCause = cause
        completedTime = Instant.now()
        status = RunStatus.Failed
        return this
    }
}

enum class RunStatus {
    Pending,
    Successful,
    Failed
}

@Embeddable
data class MatchStatus(
    var sourceOnly: Int = 0,
    var targetOnly: Int = 0,
    var bothMatched: Int = 0,
    var bothMismatched: Int = 0
) {
    @get:Transient
    val sourceTotal: Int
        get() = sourceOnly + bothMatched + bothMismatched

    @get:Transient
    val targetTotal: Int
        get() = targetOnly + bothMatched + bothMismatched

    @get:Transient
    val total: Int
        get() = sourceTotal + targetOnly
}

@Embeddable
@Suppress("JpaAttributeTypeInspection") // False positive
data class DatasetMeta(@field:TypeDef(type = DataType.STRING, converter = ColsConverter::class) var cols: List<ColMeta> = emptyList())

data class ColMeta(val name: String, val javaType: String)

/**
 * This manual conversion to JSON was required because it was not possible at time of writing to bind JSON types
 * automatically, as theoretically should be possible with Micronaut Data and R2DBC per
 * https://micronaut-projects.github.io/micronaut-data/latest/guide/#sqlJsonType
 *
 * On H2, The JsonCode is not present in 0.8.x and required a bump to 0.9 Milestone releases which didn't have stable
 * drivers for Postgres. On Postgres there is then something wrong with the binding.
 *
 * It should be possible to remove this and replace the TypeDef with type = DataType.JSON at some point.
 */
@Singleton
class ColsConverter : AttributeConverter<List<ColMeta>, String?> {
    private val objectMapper = jacksonObjectMapper()

    override fun convertToPersistedValue(entityValue: List<ColMeta>?, context: ConversionContext): String? {
        return objectMapper.writeValueAsString(entityValue ?: emptyList<ColMeta>())
    }

    override fun convertToEntityValue(persistedValue: String?, context: ConversionContext): List<ColMeta>? {
        return if (persistedValue == null) emptyList() else objectMapper.readValue(persistedValue)
    }
}
