package recce.server.dataset.datasource

import io.micronaut.data.r2dbc.operations.R2dbcOperations
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@MicronautTest(environments = arrayOf("test-integration"))
class ReactiveDataSourceTest : DataSourceTest() {
    @Inject
    @field:Named("reactive-source")
    private lateinit var sourceOperations: R2dbcOperations

    @Inject
    @field:Named("reactive-target")
    private lateinit var targetOperations: R2dbcOperations

    @Test
    fun `should load data from reactive datasource`() {
        StepVerifier.create(getCount(sourceOperations))
            .expectNext(3)
            .verifyComplete()

        StepVerifier.create(getCount(targetOperations))
            .expectNext(4)
            .verifyComplete()
    }

    private fun getCount(operations: R2dbcOperations) =
        Mono.from(operations.connectionFactory().create())
            .flatMapMany { it.createStatement("SELECT count(*) as count from TESTDATA").execute() }
            .flatMap { result -> result.map { row, _ -> row.get("count") as Long } }
}
