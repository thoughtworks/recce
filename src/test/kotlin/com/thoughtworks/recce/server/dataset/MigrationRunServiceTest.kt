package com.thoughtworks.recce.server.dataset

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

internal class MigrationRunServiceTest {
    @Test
    fun `start should throw on missing dataset`() {
        assertThatThrownBy { MigrationRunService(mock(), mock(), mock()).start("test-dataset") }
            .isExactlyInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("test-dataset")
    }
}
