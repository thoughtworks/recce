package recce.server.auth

import io.micronaut.context.ApplicationContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

internal class AuthConfigurationTest {
    private val properties =
        mutableMapOf<String, Any>("auth.username" to "test-user", "auth.password" to "test-password")

    @Test
    fun `can override defaults from config`() {
        with(ApplicationContext.run(properties)) {
            val configuration = getBean(AuthConfiguration::class.java)
            assertThat(configuration.username).isEqualTo("test-user")
            assertThat(configuration.password).isEqualTo("test-password")
        }
    }

    @Test
    fun `validate at start`() {
        with(ApplicationContext.run(mutableMapOf<String, Any>("auth.username" to " "))) {
            assertThatThrownBy { getBean(AuthConfiguration::class.java) }
                .rootCause()
                .hasMessageContaining("username - must not be blank")
        }
        with(ApplicationContext.run(mutableMapOf<String, Any>("auth.password" to " "))) {
            assertThatThrownBy { getBean(AuthConfiguration::class.java) }
                .rootCause()
                .hasMessageContaining("password - must not be blank")
        }
    }
}
