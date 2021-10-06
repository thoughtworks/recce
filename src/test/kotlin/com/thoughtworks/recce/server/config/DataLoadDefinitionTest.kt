package com.thoughtworks.recce.server.config

import io.micronaut.context.BeanLocator
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.data.r2dbc.operations.R2dbcOperations
import io.micronaut.inject.qualifiers.Qualifiers
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.util.*

internal class DataLoadDefinitionTest {
    private val testSourceName = "source1"
    private lateinit var definition: DataLoadDefinition

    @BeforeEach
    fun setUp() {
        definition = DataLoadDefinition(testSourceName, "")
    }

    @Test
    fun `should populate db operations from context`() {
        val operations = mock<R2dbcOperations>()
        val beanLocator = mock<BeanLocator> {
            on { findBean(any<Class<Any>>(), eq(Qualifiers.byName(testSourceName))) } doReturn Optional.of(operations)
        }

        definition.populate(beanLocator)

        assertThat(definition.dbOperations).isEqualTo(operations)
    }

    @Test
    fun `should throw on failure to find bean`() {

        assertThatThrownBy { definition.populate(mock()) }
            .isExactlyInstanceOf(ConfigurationException::class.java)
            .hasMessageContaining("source1")
    }
}
