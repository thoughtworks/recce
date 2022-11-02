package recce.server

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MicronautTest(transactional = false)
internal class BuildInfoConfigurationTest {
    @Inject
    lateinit var config: BuildInfoConfiguration

    @Test
    fun `should be able to retrieve version property`() {
        assertThat(config.version).isEqualTo("test-version")
    }
}
