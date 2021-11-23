package recce.server.dataset

import com.google.common.hash.HashCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Condition
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.params.provider.EnumSource
import recce.server.dataset.DataLoadDefinition.Companion.migrationKeyColumnName
import recce.server.recrun.ColMeta
import recce.server.recrun.DatasetMeta
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.time.Instant
import java.util.stream.Stream

val hexSha256Hash = Condition(::isHexSha256Hash, "is SHA56 hash encoded as hex")

private fun isHexSha256Hash(value: String): Boolean {
    val isSha256 = value.length == (256 / 8) * 2
    try {
        HashCode.fromString(value)
    } catch (ignore: Exception) {
        return false
    }
    return isSha256
}

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

        assertThatThrownBy { HashingStrategy.TypeStrict.build(row, meta) }
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

        assertThatThrownBy { HashingStrategy.TypeStrict.build(row, meta) }
            .isExactlyInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("No column named MigrationKey found in dataset")
    }

    @Test
    fun `should throw on duplicate migration key column`() {
        val (row, meta) = rowMetaWithTestCol
            .withCol(migrationKeyColumnName, String::class.java)
            .withRowValues("key", "test-val", "key")
            .build()

        assertThatThrownBy { HashingStrategy.TypeStrict.build(row, meta) }
            .isExactlyInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("More than one column named MigrationKey found in dataset")
    }

    @Test
    fun `should throw on unrecognized type`() {
        val (row, meta) = rowMetaWithTestCol.withRowValues("key", Instant.now()).build()
        assertThatThrownBy { HashingStrategy.TypeStrict.build(row, meta) }
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

        val stringTypeRow = HashingStrategy.TypeStrict.build(stringRow, stringMeta)
        val intTypeRow = HashingStrategy.TypeStrict.build(intRow, intMeta)

        assertThat(stringTypeRow.hashedValue).isNotEqualTo(intTypeRow.hashedValue)
    }

    @Test
    fun `consecutive fields should always lead to different hashes`() {

        val (row, meta) = R2dbcFakeBuilder()
            .withCol("first", String::class.java)
            .withCol("second", String::class.java)
            .withRowValues(1, "abc", "def")
            .build()

        val (row2, meta2) = R2dbcFakeBuilder()
            .withCol("first", String::class.java)
            .withCol("second", String::class.java)
            .withRowValues(1, "ab", "cdef")
            .build()

        assertThat(HashingStrategy.TypeStrict.build(row, meta).hashedValue)
            .isNotEqualTo(HashingStrategy.TypeStrict.build(row2, meta2).hashedValue)
    }

    @ParameterizedTest
    @ArgumentsSource(EquivalentTypeExamples::class)
    fun `lenient type equivalence strategy should consider similar types equal`(first: Any, second: Any) {
        val (row, meta) = R2dbcFakeBuilder()
            .withCol("test", first.javaClass)
            .withRowValues("key", first)
            .build()

        val (row2, meta2) = R2dbcFakeBuilder()
            .withCol("test", second.javaClass)
            .withRowValues("key", second)
            .build()

        assertThat(HashingStrategy.TypeLenient.build(row, meta).hashedValue)
            .describedAs("${first.javaClass}($first) should be equivalent to ${second.javaClass}($second)")
            .isEqualTo(HashingStrategy.TypeLenient.build(row2, meta2).hashedValue)
    }

    class EquivalentTypeExamples: ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> {
            return listOf(
                Arguments.of(true, java.lang.Byte.valueOf(1)),
                Arguments.of(Integer.valueOf(10), java.lang.Long.valueOf(10)),
                Arguments.of(Integer.valueOf(10), java.lang.Short.valueOf(10)),
                Arguments.of(Integer.valueOf(10), java.lang.Byte.valueOf(10)),
                Arguments.of(java.lang.Float.valueOf(10.0f), java.lang.Double.valueOf(10.0)),
            ).stream()
        }
    }

    @ParameterizedTest
    @ArgumentsSource(TypeExamples::class)
    fun `should hash all column types`(type: Class<Any>, input: Any?, expectedHash: String) {
        val (row, meta) = R2dbcFakeBuilder()
            .withCol("test", type)
            .withRowValues("key", input)
            .build()
        assertThat(HashingStrategy.TypeStrict.build(row, meta)).isEqualTo(HashedRow("key", expectedHash, meta))
    }

    class TypeExamples: ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> {
            return listOf(
                Arguments.of(
                    String::class.java,
                    null,
                    "952d0be4c421a5d1884a1ad4eb793a8965f6a040213ec77912a44266031c0b06"
                ),
                Arguments.of(
                    Boolean::class.java,
                    true,
                    "682303176353229c2c632ce24265540f4463273d099811b69587dae97f7f0380"
                ),
                Arguments.of(
                    BigDecimal::class.java,
                    BigDecimal.TEN,
                    "924936abed2b21ffb445cd2869d15df71fb68cd57aa6c69c3ad85c31db608500"
                ),
                Arguments.of(
                    Byte::class.java,
                    java.lang.Byte.valueOf(1),
                    "682303176353229c2c632ce24265540f4463273d099811b69587dae97f7f0380"
                ),
                Arguments.of(
                    Short::class.java,
                    Integer.valueOf(10).toShort(),
                    "2a709cf5b3fd1c874eb74937f933bac5e4cefa26fdb399a32939303b627b4b7d"
                ),
                Arguments.of(
                    Integer::class.java,
                    Integer.valueOf(10),
                    "2719b104b28ab4c56f21aa222fe80072316fc35e1bb12959482395a292a06143"
                ),
                Arguments.of(
                    Long::class.java,
                    Integer.valueOf(10).toLong(),
                    "eeba3b4843c0b0995034ddf5fefbf6e61ad0d29c8448203f648c4a00397066a5"
                ),
                Arguments.of(
                    Float::class.java,
                    java.lang.Float.valueOf(10.0f),
                    "a543eb24da968356ffb9975711a0b48e424e896b883cdd98eb2dc2c6516a2a5e"
                ),
                Arguments.of(
                    Double::class.java,
                    java.lang.Double.valueOf(10.0),
                    "70fb748e70c976827972c5a7e6a0a5d245a064482fceecacefcbdabc7ae800c8"
                ),
                Arguments.of(
                    String::class.java,
                    "10",
                    "24b014ca353b4654ad3a68c8b7943cb4b5493cde3667b4b25821cb9701bab250"
                ),
                Arguments.of(
                    ByteBuffer::class.java,
                    ByteBuffer.wrap("10".toByteArray(Charsets.UTF_8)),
                    "24b014ca353b4654ad3a68c8b7943cb4b5493cde3667b4b25821cb9701bab250"
                ),
            ).stream()
        }
    }
}
