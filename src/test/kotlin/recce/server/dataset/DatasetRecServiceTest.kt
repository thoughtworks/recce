package recce.server.dataset

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

internal class DatasetRecServiceTest {
    @Test
    fun `start should throw on missing dataset`() {
        assertThatThrownBy { DatasetRecService(mock(), mock(), mock()).runFor("test-dataset") }
            .isExactlyInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("test-dataset")
    }
}
