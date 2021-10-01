package com.thoughtworks.recce.server.config

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MicronautTest(propertySources = arrayOf("classpath:configuration/application-test-dataset.yml"))
class ReconciliationConfigurationTest {

    @Inject
    lateinit var config: ReconciliationConfiguration

    @Test
    fun `should parse dataset configuration from yaml`() {
        assertThat(config.dataSets)
            .hasSize(1)
            .hasEntrySatisfying("test-dataset") {
                assertThat(it.source.dataSourceRef).isEqualTo("source")
                assertThat(it.source.query).contains("sourcedatacount")
                assertThat(it.target.dataSourceRef).isEqualTo("target")
                assertThat(it.target.query).contains("targetdatacount")
            }
    }
}
