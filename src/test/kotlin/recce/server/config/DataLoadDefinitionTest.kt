package recce.server.config

import io.micronaut.context.BeanLocator
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.data.r2dbc.operations.R2dbcOperations
import io.micronaut.inject.qualifiers.Qualifiers
import io.r2dbc.spi.Connection
import io.r2dbc.spi.Result
import io.r2dbc.spi.Statement
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.kotlin.*
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*

internal class DataLoadDefinitionTest {
    private val testSourceName = "source1"
    private val testQuery = "select * from somewhere"
    private lateinit var definition: DataLoadDefinition

    private val mockConnection: Connection = mock {
        on { close() } doReturn Mono.empty()
    }

    @BeforeEach
    fun setUp() {
        definition = DataLoadDefinition(testSourceName, testQuery)
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

    @Test
    fun `should stream rows from query`() {

        val result = mock<Result>()

        val statement: Statement = mock {
            on { execute() } doReturn Mono.just(result)
        }

        definition.dbOperations = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS) {
            on { connectionFactory().create() } doReturn Mono.just(mockConnection)
        }

        whenever(mockConnection.createStatement(eq(testQuery))).thenReturn(statement)

        StepVerifier.create(definition.runQuery())
            .expectNext(result)
            .verifyComplete()

        val inOrder = inOrder(mockConnection, statement)
        inOrder.verify(mockConnection).createStatement(eq(testQuery))
        inOrder.verify(statement).execute()
        inOrder.verify(mockConnection).close()
    }

    @Test
    fun `should close connection after failed query`() {
        definition.dbOperations = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS) {
            on { connectionFactory().create() } doReturn Mono.just(mockConnection)
        }

        StepVerifier.create(definition.runQuery())
            .expectErrorSatisfies {
                assertThat(it)
                    .isInstanceOf(NullPointerException::class.java)
                    .hasMessageContaining("Cannot invoke \"io.r2dbc.spi.Statement.execute()")
            }
            .verify()

        val inOrder = inOrder(mockConnection)
        inOrder.verify(mockConnection).createStatement(eq(testQuery))
        inOrder.verify(mockConnection).close()
    }
}
