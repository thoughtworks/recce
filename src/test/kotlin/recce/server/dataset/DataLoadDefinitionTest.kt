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
    private val testDatasetId = "test-dataset"
    private val testSourceName = "source1"
    private val testQuery = "SELECT * FROM somewhere"
    private val testQueryStatementFromFile = "SELECT * FROM elsewhere\n"
    private val testQueryFile = Resources.getResource("queries/test-dataset-source.sql").path
    private val testQueryFileBaseDir = Resources.getResource("queries").path
    private val testQueryInvalidFile = "test-invalid-query.sql"

    private lateinit var definitionQuery: DataLoadDefinition

    private val mockConnection: Connection = mock {
        on { close() } doReturn Mono.empty()
    }

    @BeforeEach
    fun setUp() {
        definitionQuery =
            DataLoadDefinition(testSourceName, Optional.of(testQuery)).apply { role = DataLoadRole.Source }
    }

    @Test
    fun `should load query statement from query sql if query sql, query file and query file base directory provided`() {
        definitionQuery.queryFileBaseDir = Optional.of(Path(testQueryFileBaseDir))
        definitionQuery.query = Optional.of(testQuery)
        definitionQuery.queryFile = Optional.of(Path(testQueryFile))

        assertThat(definitionQuery.resolveQueryStatement()).isEqualTo(testQuery)
    }

    @Test
    fun `should load query statement from query file if query file and query file base directory provided`() {
        definitionQuery.role = DataLoadRole.Source
        definitionQuery.queryFileBaseDir = Optional.empty()
        definitionQuery.query = Optional.empty()
        definitionQuery.queryFile = Optional.of(Path(testQueryFile))

        assertThat(definitionQuery.resolveQueryStatement()).isEqualTo(testQueryStatementFromFile)
    }

    @Test
    fun `should load query statement from query file base directory if only query file base directory provided`() {
        definitionQuery.datasetId = testDatasetId
        definitionQuery.role = DataLoadRole.Source
        definitionQuery.queryFileBaseDir = Optional.of(Path(testQueryFileBaseDir))
        definitionQuery.query = Optional.empty()
        definitionQuery.queryFile = Optional.empty()

        assertThat(definitionQuery.resolveQueryStatement()).isEqualTo(testQueryStatementFromFile)
    }

    @Test
    fun `should fail to load query statement from file if file not found`() {
        definitionQuery.role = DataLoadRole.Source
        definitionQuery.queryFileBaseDir = Optional.empty()
        definitionQuery.query = Optional.empty()
        definitionQuery.queryFile = Optional.of(Path(testQueryInvalidFile))

        assertThatThrownBy { definitionQuery.resolveQueryStatement() }
            .isExactlyInstanceOf(ConfigurationException::class.java)
            .hasMessageContaining("Cannot load query")

        definitionQuery.datasetId = testDatasetId
        definitionQuery.role = DataLoadRole.Target
        definitionQuery.queryFileBaseDir = Optional.empty()
        definitionQuery.query = Optional.empty()
        definitionQuery.queryFile = Optional.empty()

        assertThatThrownBy { definitionQuery.resolveQueryStatement() }
            .isExactlyInstanceOf(ConfigurationException::class.java)
            .hasMessageContaining("Cannot load query")
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
