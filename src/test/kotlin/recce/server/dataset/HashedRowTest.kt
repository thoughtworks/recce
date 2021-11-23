package recce.server.dataset

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import recce.server.recrun.ColMeta
import recce.server.recrun.DatasetMeta

internal class HashedRowTest {
    @Test
    fun `should dynamically convert row metadata`() {
        val rowMeta = R2dbcFakeBuilder().withCol("test", String::class.java).buildMeta()
        val row = HashedRow("test", "test", rowMeta)

        val expectedMeta = DatasetMeta(
            listOf(
                ColMeta(DataLoadDefinition.migrationKeyColumnName, "String"),
                ColMeta("test", "String")
            )
        )
        Assertions.assertThat(row.lazyMeta()()).usingRecursiveComparison().isEqualTo(expectedMeta)
    }
}
