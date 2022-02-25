package recce.server

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.ConfigurationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
    fun `throw exception if datasourceRef does not exist when retrieving url`() {
        val datasourceRef = "non-existent-datasource"
        with(ApplicationContext.run(properties)) {
            val config = getBean(R2dbcConfiguration::class.java)
            assertThatThrownBy { config.getUrl(datasourceRef) }
                .isExactlyInstanceOf(ConfigurationException::class.java)
                .hasMessageContaining(datasourceRef)
        }
    }
}
