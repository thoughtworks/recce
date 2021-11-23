package recce.server

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.context.exceptions.ConfigurationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import recce.server.dataset.DataLoadRole
import recce.server.dataset.HashingStrategy

// Faster tests that do not load the full configuration and are quicker to iterate on when testing
// configuration binding
internal class RecConfigurationPropertiesTest {

    private val properties = mutableMapOf<String, Any>(
        "flyway.datasources.default.enabled" to "false",
        "r2dbc.datasources.source.url" to "r2dbc:h2:mem:///sourceDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE",
        "r2dbc.datasources.target.url" to "r2dbc:h2:mem:///targetDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE",
        "reconciliation.datasets.test-dataset.hashingStrategy" to "TypeStrict",
        "reconciliation.datasets.test-dataset.schedule.cronExpression" to "0 0 0 ? * *",
        "reconciliation.datasets.test-dataset.source.dataSourceRef" to "source",
        "reconciliation.datasets.test-dataset.source.query" to "select count(*) as sourcedatacount from testdata",
        "reconciliation.datasets.test-dataset.target.dataSourceRef" to "target",
        "reconciliation.datasets.test-dataset.target.query" to "select count(*) as targetdatacount from testdata",
    )

    @Test
    fun `should parse from properties`() {
        val ctx = ApplicationContext.run(properties)

        assertThat(ctx.getBean(RecConfiguration::class.java).datasets.values)
            .hasSize(1)
            .first().satisfies {
                assertThat(it.name).isEqualTo("test-dataset")
                assertThat(it.hashingStrategy).isEqualTo(HashingStrategy.TypeStrict)
                assertThat(it.schedule.cronExpression).isEqualTo("0 0 0 ? * *")
                assertThat(it.source.role).isEqualTo(DataLoadRole.source)
                assertThat(it.source.dataSourceRef).isEqualTo("source")
                assertThat(it.source.query).contains("sourcedatacount")
                assertThat(it.source.dbOperations).isNotNull
                assertThat(it.target.role).isEqualTo(DataLoadRole.target)
                assertThat(it.target.dataSourceRef).isEqualTo("target")
                assertThat(it.target.query).contains("targetdatacount")
                assertThat(it.target.dbOperations).isNotNull
            }
    }

    @Test
    fun `should fail load on incorrect dataSourceRef`() {
        properties["reconciliation.datasets.test-dataset.source.dataSourceRef"] = "non-existent-datasource"

        assertThatThrownBy { ApplicationContext.run(properties) }
            .isExactlyInstanceOf(BeanInstantiationException::class.java)
            .hasMessageContaining("non-existent-datasource")
            .hasRootCauseExactlyInstanceOf(ConfigurationException::class.java)
            .hasMessageContaining("non-existent-datasource")
    }
}
