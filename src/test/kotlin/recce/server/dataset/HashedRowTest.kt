package recce.server.dataset

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import recce.server.recrun.ColMeta
import recce.server.recrun.DatasetMeta

internal class HashedRowTest {
    @Test
    fun `should dynamically convert row metadata`() {
        val columnMetas = R2dbcFakeBuilder().withCol("test", String::class.java).buildColMetas()
        val row = HashedRow("test", "test", columnMetas)

        val expectedMeta =
            DatasetMeta(
                listOf(
                    ColMeta(DataLoadDefinition.MIGRATION_KEY_COLUMN_NAME, "String"),
                    ColMeta("test", "String")
                )
            )
        Assertions.assertThat(row.lazyMeta()()).usingRecursiveComparison().isEqualTo(expectedMeta)
    }
}
