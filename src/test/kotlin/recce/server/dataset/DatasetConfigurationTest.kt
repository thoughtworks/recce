package recce.server.dataset

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class DatasetConfigurationTest {

    @Test
    fun `should produce datasource descriptor`() {
        val conf =
            DatasetConfiguration(
                DataLoadDefinition("source", "blah"),
                DataLoadDefinition("target", "blah")
            )
        assertThat(conf.datasourceDescriptor)
            .isEqualTo("(source -> target)")
    }
}
