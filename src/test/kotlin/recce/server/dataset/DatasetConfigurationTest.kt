package recce.server.dataset

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class DatasetConfigurationTest {

    @Test
    fun `should produce short descriptor`() {
        val conf =
            DatasetConfiguration(
                DataLoadDefinition("source", "blah"),
                DataLoadDefinition("target", "blah")
            ).apply { name = "my-dataset" }
        assertThat(conf.shortDescriptor)
            .isEqualTo("my-dataset(source->target")
    }
}
