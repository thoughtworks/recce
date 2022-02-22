package recce.server

import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter
import io.micronaut.core.annotation.Introspected
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class R2dbcConfiguration @Inject constructor(sources: List<R2dbcDatasource>) {
    private var datasources: Map<String, String>

    init {
        this.datasources = sources.associate { it.name to it.url }
    }

    fun getUrl(datasourceRef: String): String =
        datasources[datasourceRef] ?: "no url found for datasourceRef: $datasourceRef"
}

@Introspected
@EachProperty("r2dbc.datasources")
class R2dbcDatasource
constructor(@param:Parameter val name: String) {
    lateinit var url: String
}

