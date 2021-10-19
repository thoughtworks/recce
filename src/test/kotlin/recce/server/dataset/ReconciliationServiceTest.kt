package recce.server.dataset

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

internal class ReconciliationServiceTest {
    @Test
    fun `start should throw on missing dataset`() {
        assertThatThrownBy { ReconciliationService(mock(), mock(), mock()).runFor("test-dataset") }
            .isExactlyInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("test-dataset")
    }
}
