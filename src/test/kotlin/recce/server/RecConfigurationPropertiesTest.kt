package recce.server

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.context.exceptions.ConfigurationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import recce.server.dataset.DataLoadRole
import recce.server.dataset.HashingStrategy
import java.util.function.Consumer

// Faster tests that do not load the full configuration and are quicker to iterate on when testing
// configuration binding
internal class RecConfigurationPropertiesTest {

    private val properties = mutableMapOf<String, Any>(
        "flyway.datasources.default.enabled" to "false",
        "r2dbc.datasources.source.url" to "r2dbc:h2:mem:///sourceDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE",
        "r2dbc.datasources.target.url" to "r2dbc:h2:mem:///targetDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE",
        "reconciliation.datasets.test-dataset.hashingStrategy" to "TypeStrict",
        "reconciliation.datasets.test-dataset.schedule.cronExpression" to "0 0 0 ? * *",
        "reconciliation.datasets.test-dataset.source.datasourceRef" to "source",
        "reconciliation.datasets.test-dataset.source.query" to "SELECT count(*) AS sourcedatacount FROM testdata",
        "reconciliation.datasets.test-dataset.target.datasourceRef" to "target",
        "reconciliation.datasets.test-dataset.target.query" to "SELECT count(*) AS targetdatacount FROM testdata",
    )

    @Test
    fun `can override defaults from config`() {
        with(ApplicationContext.run(properties)) {
            val configuration = getBean(RecConfiguration::class.java)
            assertThat(configuration.defaults.batchSize).isEqualTo(1000)
            assertThat(configuration.defaults.batchConcurrency).isEqualTo(5)
            assertThat(configuration.defaults.hashingStrategy).isEqualTo(HashingStrategy.TypeLenient)
            assertThat(getBean(DefaultsProvider::class.java).hashingStrategy).isEqualTo(HashingStrategy.TypeLenient)
        }

        properties["reconciliation.defaults.batchSize"] = "3000"
        properties["reconciliation.defaults.batchConcurrency"] = "10"
        properties["reconciliation.defaults.hashingStrategy"] = "TypeStrict"

        with(ApplicationContext.run(properties)) {
            val configuration2 = getBean(RecConfiguration::class.java)
            assertThat(configuration2.defaults.batchSize).isEqualTo(3000)
            assertThat(configuration2.defaults.batchConcurrency).isEqualTo(10)
            assertThat(configuration2.defaults.hashingStrategy).isEqualTo(HashingStrategy.TypeStrict)
            assertThat(getBean(DefaultsProvider::class.java).hashingStrategy).isEqualTo(HashingStrategy.TypeStrict)
        }
    }

    @Test
    fun `should parse datasets with overrides from properties`() {
        val ctx = ApplicationContext.run(properties)

        assertThat(ctx.getBean(RecConfiguration::class.java).datasets.values)
            .singleElement()
            .satisfies(
                Consumer {
                    assertThat(it.id).isEqualTo("test-dataset")
                    assertThat(it.resolvedHashingStrategy).isEqualTo(HashingStrategy.TypeStrict)
                    assertThat(it.schedule.cronExpression).isEqualTo("0 0 0 ? * *")
                    assertThat(it.source.role).isEqualTo(DataLoadRole.Source)
                    assertThat(it.source.datasourceRef).isEqualTo("source")
                    assertThat(it.source.query).contains("sourcedatacount")
                    assertThat(it.source.dbOperations).isNotNull
                    assertThat(it.target.role).isEqualTo(DataLoadRole.Target)
                    assertThat(it.target.datasourceRef).isEqualTo("target")
                    assertThat(it.target.query).contains("targetdatacount")
                    assertThat(it.target.dbOperations).isNotNull
                }
            )
    }

    @Test
    fun `should fail load on incorrect datasourceRef`() {
        properties["reconciliation.datasets.test-dataset.source.datasourceRef"] = "non-existent-datasource"

        assertThatThrownBy { ApplicationContext.run(properties) }
            .isExactlyInstanceOf(BeanInstantiationException::class.java)
            .hasMessageContaining("non-existent-datasource")
            .hasRootCauseExactlyInstanceOf(ConfigurationException::class.java)
            .hasMessageContaining("non-existent-datasource")
    }
}
