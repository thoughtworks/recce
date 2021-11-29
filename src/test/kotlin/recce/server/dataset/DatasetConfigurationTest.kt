package recce.server.dataset

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.*

internal class DatasetConfigurationTest {

    private val conf =
        DatasetConfiguration(
            DataLoadDefinition("source", "blah"),
            DataLoadDefinition("target", "blah")
        )

    @Test
    fun `should produce datasource descriptor`() {
        assertThat(conf.datasourceDescriptor)
            .isEqualTo("(source -> target)")
    }

    @Test
    fun `should resolving hashing strategy from defaults`() {
        assertThat(conf.resolvedHashingStrategy).isEqualTo(HashingStrategy.TypeLenient)

        assertThat(
            DatasetConfiguration(
                DataLoadDefinition("source", "blah"),
                DataLoadDefinition("target", "blah"),
                hashingStrategy = Optional.of(HashingStrategy.TypeStrict)
            ).resolvedHashingStrategy
        ).isEqualTo(HashingStrategy.TypeStrict)
    }
}
