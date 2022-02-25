package recce.server

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MicronautTest(propertySources = ["classpath:build-info.properties"])
internal class VersionConfigurationTest {
    @Inject
    lateinit var config: BuildInfoConfiguration

    @Test
    fun `version property is semantic version compliant`() {
        println("classpath ${System.getProperty("java.class.path")}")
        println("config.version ${config.version}")
        assertThat(config.version).isNotBlank
    }
}
