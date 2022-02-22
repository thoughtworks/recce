package recce.server

import io.micronaut.context.ApplicationContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class R2dbcConfigurationTest {

    private val properties = mapOf(
        "r2dbc.datasources.source.url" to "r2dbc:h2:mem:///sourceDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE",
        "r2dbc.datasources.target.url" to "r2dbc:h2:mem:///targetDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE",
    )

    @Test
    fun `retrieve url for valid data sources`() {
        with(ApplicationContext.run(properties)) {
            val config = getBean(R2dbcConfiguration::class.java)
            assertThat(config.getUrl("source")).isEqualTo("r2dbc:h2:mem:///sourceDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE")
            assertThat(config.getUrl("target")).isEqualTo("r2dbc:h2:mem:///targetDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE")
        }
    }

    @Test
    fun `return default message for non-existent datasource when retrieving url`() {
        with(ApplicationContext.run(properties)) {
            val config = getBean(R2dbcConfiguration::class.java)
            assertThat(config.getUrl("non-existent-datasource")).isEqualTo("no url found for datasourceRef: non-existent-datasource")
        }
    }
}
