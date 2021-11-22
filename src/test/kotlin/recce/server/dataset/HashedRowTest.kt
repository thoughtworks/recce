package recce.server.dataset

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import recce.server.dataset.DataLoadDefinition.Companion.migrationKeyColumnName
import recce.server.recrun.ColMeta
import recce.server.recrun.DatasetMeta
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.time.Instant

internal class HashedRowTest {

    private val rowMetaWithTestCol = R2dbcFakeBuilder()
        .withCol("test", String::class.java)

    @Test
    fun `should dynamically convert row metadata`() {
        val row = HashedRow("test", "test", rowMetaWithTestCol.buildMeta())

        val expectedMeta = DatasetMeta(
            listOf(
                ColMeta(migrationKeyColumnName, "String"),
                ColMeta("test", "String")
            )
        )
        assertThat(row.lazyMeta()()).usingRecursiveComparison().isEqualTo(expectedMeta)
    }

    @Test
    fun `should throw on null migration key`() {

        val (row, meta) = rowMetaWithTestCol
            .withRowValues(null, "test-val")
            .build()

        assertThatThrownBy { HashedRow.fromRow(row, meta) }
            .isExactlyInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("MigrationKey has null value somewhere in dataset")
    }

    @Test
    fun `should throw on missing migration key column`() {
        val (row, meta) = R2dbcFakeBuilder()
            .noMigrationKey()
            .withCol("test", String::class.java)
            .withRowValues("test-val")
            .build()

        assertThatThrownBy { HashedRow.fromRow(row, meta) }
            .isExactlyInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("No column named MigrationKey found in dataset")
    }

    @Test
    fun `should throw on duplicate migration key column`() {
        val (row, meta) = rowMetaWithTestCol
            .withCol(migrationKeyColumnName, String::class.java)
            .withRowValues("key", "test-val", "key")
            .build()

        assertThatThrownBy { HashedRow.fromRow(row, meta) }
            .isExactlyInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("More than one column named MigrationKey found in dataset")
    }

    @Test
    fun `should throw on unrecognized type`() {
        val (row, meta) = rowMetaWithTestCol.withRowValues("key", Instant.now()).build()
        assertThatThrownBy { HashedRow.fromRow(row, meta) }
            .isExactlyInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Instant")
            .hasMessageContaining("test")
    }

    @Test
    fun `nulls of different defined column java types should be considered unequal`() {
        val (stringRow, stringMeta) = R2dbcFakeBuilder()
            .withCol("test", String::class.java)
            .withRowValues("key", null)
            .build()

        val (intRow, intMeta) = R2dbcFakeBuilder()
            .withCol("test", Integer::class.java)
            .withRowValues("key", null)
            .build()

        val stringTypeRow = HashedRow.fromRow(stringRow, stringMeta)
        val intTypeRow = HashedRow.fromRow(intRow, intMeta)

        assertThat(stringTypeRow.hashedValue).isNotEqualTo(intTypeRow.hashedValue)
    }

    @ParameterizedTest
    @MethodSource("types")
    fun `should hash all column types`(type: Class<Any>, input: Any?, expectedHash: String) {
        val (row, meta) = R2dbcFakeBuilder()
            .withCol("test", type)
            .withRowValues("key", input)
            .build()
        assertThat(HashedRow.fromRow(row, meta)).isEqualTo(HashedRow("key", expectedHash, meta))
    }

    companion object {
        @JvmStatic
        fun types() = listOf(
            Arguments.of(
                String::class.java,
                null,
                "ca7f6cfcd4f417dbff9cea143b1071decb72f8ec40eb6aa33856224e0e07df7e"
            ),
            Arguments.of(
                Boolean::class.java,
                true,
                "4bf5122f344554c53bde2ebb8cd2b7e3d1600ad631c385a5d7cce23c7785459a"
            ),
            Arguments.of(
                BigDecimal::class.java,
                BigDecimal.TEN,
                "596c0ad17b38f4bf6c899f6b02c82f9ee326cfb7ac2d9775f49a88163364882b"
            ),
            Arguments.of(
                Byte::class.java,
                java.lang.Byte.valueOf(1),
                "4bf5122f344554c53bde2ebb8cd2b7e3d1600ad631c385a5d7cce23c7785459a"
            ),
            Arguments.of(
                Short::class.java,
                Integer.valueOf(10).toShort(),
                "102b51b9765a56a3e899f7cf0ee38e5251f9c503b357b330a49183eb7b155604"
            ),
            Arguments.of(
                Integer::class.java,
                Integer.valueOf(10),
                "075de2b906dbd7066da008cab735bee896370154603579a50122f9b88545bd45"
            ),
            Arguments.of(
                Long::class.java,
                Integer.valueOf(10).toLong(),
                "a111f275cc2e7588000001d300a31e76336d15b9d314cd1a1d8f3d3556975eed"
            ),
            Arguments.of(
                Float::class.java,
                Integer.valueOf(10).toFloat(),
                "80c8a717ccd70c8809eb78e6a9591c003e11c721fe0ccaf62fd592abda1a5593"
            ),
            Arguments.of(
                Double::class.java,
                Integer.valueOf(10).toDouble(),
                "24b1f4ef66b650ff816e519b01742ff1753733d36e1b4c3e3b52743168915b1f"
            ),
            Arguments.of(
                String::class.java,
                "10",
                "4a44dc15364204a80fe80e9039455cc1608281820fe2b24f1e5233ade6af1dd5"
            ),
            Arguments.of(
                ByteBuffer::class.java,
                ByteBuffer.wrap("10".toByteArray(Charsets.UTF_8)),
                "4a44dc15364204a80fe80e9039455cc1608281820fe2b24f1e5233ade6af1dd5"
            ),
        )
    }
}
