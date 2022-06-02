package recce.server.dataset

import com.google.common.io.Resources
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
import reactor.kotlin.test.test
import java.util.*
import kotlin.io.path.Path

internal class DataLoadDefinitionTest {
    private val testSourceName = "source1"
    private val testQuery = "SELECT * FROM somewhere"
    private val testQueryStatementFromFile = "SELECT * FROM elsewhere\n"
    private val testQueryFile = Resources.getResource("config/test-query.sql").path
    private val testQueryInvalidFile = "test-invalid-query.sql"

    private lateinit var definitionQuery: DataLoadDefinition

    private val mockConnection: Connection = mock {
        on { close() } doReturn Mono.empty()
    }

    @BeforeEach
    fun setUp() {
        definitionQuery = DataLoadDefinition(testSourceName, QueryConfig(Optional.of(testQuery))).apply { role = DataLoadRole.Source }
    }

    @Test
    fun `should load query statement from file if valid query file provided`() {
        val definitionQueryFromFile =
            DataLoadDefinition(testSourceName, QueryConfig(Optional.empty<String>(), Optional.of(Path(testQueryFile)))).apply { role = DataLoadRole.Source }
        val operations = mock<R2dbcOperations>()
        val beanLocator = mock<BeanLocator> {
            on { findBean(any<Class<Any>>(), eq(Qualifiers.byName(testSourceName))) } doReturn Optional.of(operations)
        }

        definitionQueryFromFile.populate(beanLocator)

        assertThat(definitionQueryFromFile.queryStatement).isEqualTo(testQueryStatementFromFile)
    }

    @Test
    fun `should fail to load query statement from file if invalid query file provided`() {
        val definitionQueryFromInvalidFile =
            DataLoadDefinition(testSourceName, QueryConfig(Optional.empty<String>(), Optional.of(Path(testQueryInvalidFile)))).apply { role = DataLoadRole.Source }
        val operations = mock<R2dbcOperations>()
        val beanLocator = mock<BeanLocator> {
            on { findBean(any<Class<Any>>(), eq(Qualifiers.byName(testSourceName))) } doReturn Optional.of(operations)
        }

        assertThatThrownBy { definitionQueryFromInvalidFile.populate(beanLocator) }
            .isExactlyInstanceOf(ConfigurationException::class.java)
            .hasMessageContaining("Cannot load query statement from queryFile")
    }

    @Test
    fun `should load query statement from query if both query and query file provided`() {
        val definitionQueryAndQueryFromFile =
            DataLoadDefinition(testSourceName, QueryConfig(Optional.of(testQuery), Optional.of(Path(testQueryFile)))).apply { role = DataLoadRole.Source }
        val operations = mock<R2dbcOperations>()
        val beanLocator = mock<BeanLocator> {
            on { findBean(any<Class<Any>>(), eq(Qualifiers.byName(testSourceName))) } doReturn Optional.of(operations)
        }

        definitionQueryAndQueryFromFile.populate(beanLocator)

        assertThat(definitionQueryAndQueryFromFile.queryStatement).isEqualTo(testQuery)
    }

    @Test
    fun `should load query statement from query if both query and invalid query file provided`() {
        val definitionQueryAndQueryFromInvalidFile =
            DataLoadDefinition(testSourceName, QueryConfig(Optional.of(testQuery), Optional.of(Path(testQueryInvalidFile)))).apply { role = DataLoadRole.Source }
        val operations = mock<R2dbcOperations>()
        val beanLocator = mock<BeanLocator> {
            on { findBean(any<Class<Any>>(), eq(Qualifiers.byName(testSourceName))) } doReturn Optional.of(operations)
        }

        definitionQueryAndQueryFromInvalidFile.populate(beanLocator)

        assertThat(definitionQueryAndQueryFromInvalidFile.queryStatement).isEqualTo(testQuery)
    }

    @Test
    fun `should populate db operations from context`() {
        val operations = mock<R2dbcOperations>()
        val beanLocator = mock<BeanLocator> {
            on { findBean(any<Class<Any>>(), eq(Qualifiers.byName(testSourceName))) } doReturn Optional.of(operations)
        }

        definitionQuery.populate(beanLocator)

        assertThat(definitionQuery.dbOperations).isEqualTo(operations)
    }

    @Test
    fun `should produce short descriptor with role`() {
        assertThat(definitionQuery.datasourceDescriptor).isEqualTo("Source(ref=source1)")
    }

    @Test
    fun `should throw on failure to find bean`() {

        assertThatThrownBy { definitionQuery.populate(mock()) }
            .isExactlyInstanceOf(ConfigurationException::class.java)
            .hasMessageContaining("source1")
    }

    @Test
    fun `should stream rows from query`() {

        val result = mock<Result>()

        val statement: Statement = mock {
            on { execute() } doReturn Mono.just(result)
        }

        definitionQuery.dbOperations = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS) {
            on { connectionFactory().create() } doReturn Mono.just(mockConnection)
        }

        definitionQuery.queryStatement = testQuery

        whenever(mockConnection.createStatement(eq(testQuery))).thenReturn(statement)

        definitionQuery.runQuery()
            .test()
            .expectNext(result)
            .verifyComplete()

        inOrder(mockConnection, statement).apply {
            verify(mockConnection).createStatement(eq(testQuery))
            verify(statement).execute()
            verify(mockConnection).close()
        }
    }

    @Test
    fun `should close connection after failed query`() {
        definitionQuery.dbOperations = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS) {
            on { connectionFactory().create() } doReturn Mono.just(mockConnection)
        }

        definitionQuery.queryStatement = testQuery

        definitionQuery.runQuery()
            .test()
            .expectErrorSatisfies {
                assertThat(it)
                    .isInstanceOf(NullPointerException::class.java)
                    .hasMessageContaining("Cannot invoke \"io.r2dbc.spi.Statement.execute()")
            }
            .verify()

        inOrder(mockConnection).apply {
            verify(mockConnection).createStatement(eq(testQuery))
            verify(mockConnection).close()
        }
    }
}
