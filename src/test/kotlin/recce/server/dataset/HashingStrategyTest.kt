package recce.server.dataset

import com.google.common.hash.HashCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Condition
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.params.provider.EnumSource
import recce.server.dataset.DataLoadDefinition.Companion.MIGRATION_KEY_COLUMN_NAME
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.time.Instant
import java.util.*
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

internal class HashingStrategyTest {
    private val rowMetaWithTestCol =
        R2dbcFakeBuilder()
            .withCol("test", String::class.java)

    @ParameterizedTest
    @EnumSource(HashingStrategy::class)
    fun `should throw on null migration key`(strat: HashingStrategy) {
        val row =
            rowMetaWithTestCol
                .withRowValues(null, "test-val")
                .build()

        assertThatThrownBy { strat.hash(row, row.metadata) }
            .isExactlyInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("MigrationKey has null value somewhere in dataset")
    }

    @ParameterizedTest
    @EnumSource(HashingStrategy::class)
    fun `should throw on missing migration key column`(strat: HashingStrategy) {
        val row =
            R2dbcFakeBuilder()
                .noMigrationKey()
                .withCol("test", String::class.java)
                .withRowValues("test-val")
                .build()

        assertThatThrownBy { strat.hash(row, row.metadata) }
            .isExactlyInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("No column named MigrationKey found in dataset")
    }

    @ParameterizedTest
    @EnumSource(HashingStrategy::class)
    fun `should throw on duplicate migration key column`(strat: HashingStrategy) {
        val row =
            rowMetaWithTestCol
                .withCol(MIGRATION_KEY_COLUMN_NAME, String::class.java)
                .withRowValues("key", "test-val", "key")
                .build()

        assertThatThrownBy { strat.hash(row, row.metadata) }
            .isExactlyInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("More than one column named MigrationKey found in dataset")
    }

    @ParameterizedTest
    @EnumSource(HashingStrategy::class)
    fun `should throw on unrecognized type`(strat: HashingStrategy) {
        val row = rowMetaWithTestCol.withRowValues("key", Instant.now()).build()
        assertThatThrownBy { strat.hash(row, row.metadata) }
            .isExactlyInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Instant")
            .hasMessageContaining("test")
    }

    @ParameterizedTest
    @EnumSource(HashingStrategy::class)
    fun `consecutive fields should always lead to different hashes`(strat: HashingStrategy) {
        val row =
            R2dbcFakeBuilder()
                .withCol("first", String::class.java)
                .withCol("second", String::class.java)
                .withRowValues(1, "abc", "def")
                .build()

        val row2 =
            R2dbcFakeBuilder()
                .withCol("first", String::class.java)
                .withCol("second", String::class.java)
                .withRowValues(1, "ab", "cdef")
                .build()

        assertThat(strat.hash(row, row.metadata).hashedValue)
            .isNotEqualTo(strat.hash(row2, row2.metadata).hashedValue)
    }

    @ParameterizedTest
    @EnumSource(HashingStrategy::class)
    fun `strategy should dictate whether nulls of different defined column java types should be considered unequal `(
        strat: HashingStrategy
    ) {
        val stringRow =
            R2dbcFakeBuilder()
                .withCol("test", String::class.java)
                .withRowValues("key", null)
                .build()

        val intRow =
            R2dbcFakeBuilder()
                .withCol("test", Integer::class.java)
                .withRowValues("key", null)
                .build()

        val stringTypeRow = strat.hash(stringRow, stringRow.metadata)
        val intTypeRow = strat.hash(intRow, intRow.metadata)

        when (strat) {
            HashingStrategy.TypeStrict -> assertThat(stringTypeRow.hashedValue).isNotEqualTo(intTypeRow.hashedValue)
            HashingStrategy.TypeLenient -> assertThat(stringTypeRow.hashedValue).isEqualTo(intTypeRow.hashedValue)
        }
    }

    @ParameterizedTest
    @ArgumentsSource(EquivalentTypeExamples::class)
    fun `lenient type equivalence strategy should consider similar types equal`(
        first: Any,
        second: Any
    ) {
        val row =
            R2dbcFakeBuilder()
                .withCol("test", first.javaClass)
                .withRowValues("key", first)
                .build()

        val row2 =
            R2dbcFakeBuilder()
                .withCol("test", second.javaClass)
                .withRowValues("key", second)
                .build()

        assertThat(HashingStrategy.TypeLenient.hash(row, row.metadata).hashedValue)
            .describedAs(
                "lenient hash should be equal between ${first.javaClass}($first) and ${second.javaClass}($second)"
            )
            .isEqualTo(HashingStrategy.TypeLenient.hash(row2, row2.metadata).hashedValue)
    }

    class EquivalentTypeExamples : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> {
            return listOf(
                Arguments.of(true, java.lang.Byte.valueOf(1)),
                Arguments.of(Integer.valueOf(10), java.lang.Long.valueOf(10)),
                Arguments.of(Integer.valueOf(10), java.lang.Short.valueOf(10)),
                Arguments.of(Integer.valueOf(10), java.lang.Byte.valueOf(10)),
                Arguments.of(java.lang.Float.valueOf(10.0f), java.lang.Double.valueOf(10.0))
            ).stream()
        }
    }

    @ParameterizedTest
    @ArgumentsSource(TypeExamples::class)
    fun `should hash all column types`(
        type: Class<*>,
        inputSupplier: () -> Any?,
        expectedStrictHash: String,
        expectedLenientHash: Optional<String>
    ) {
        val row =
            R2dbcFakeBuilder()
                .withCol("test", type)
                .withRowValues("key", inputSupplier())
                .build()
        assertThat(HashingStrategy.TypeStrict.hash(row, row.metadata))
            .describedAs("strict hash not as expected")
            .isEqualTo(HashedRow("key", expectedStrictHash, row.metadata.columnMetadatas))

        val row2 =
            R2dbcFakeBuilder()
                .withCol("test", type)
                .withRowValues("key", inputSupplier())
                .build()
        assertThat(HashingStrategy.TypeLenient.hash(row2, row2.metadata))
            .describedAs("lenient hash not as expected")
            .isEqualTo(HashedRow("key", expectedLenientHash.orElse(expectedStrictHash), row2.metadata.columnMetadatas))
    }

    class TypeExamples : ArgumentsProvider {
        @Suppress("LongMethod")
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> {
            return listOf(
                Arguments.of(
                    String::class.java,
                    { null },
                    "952d0be4c421a5d1884a1ad4eb793a8965f6a040213ec77912a44266031c0b06",
                    Optional.of("54495118679cfbbc950dbdd9d103fc8f30d61224e30a743a7c3053628a60a943")
                ),
                Arguments.of(
                    Boolean::class.java,
                    { true },
                    "682303176353229c2c632ce24265540f4463273d099811b69587dae97f7f0380",
                    Optional.of("4bd06b61205b10f798f938a7fbbbe7873211b93278529c328f8b15a6d1e9db47")
                ),
                Arguments.of(
                    BigDecimal::class.java,
                    { BigDecimal.TEN },
                    "924936abed2b21ffb445cd2869d15df71fb68cd57aa6c69c3ad85c31db608500",
                    Optional.empty<String>()
                ),
                Arguments.of(
                    Byte::class.java,
                    { java.lang.Byte.valueOf(10) },
                    "94368a279d18c4420d7bd000f0124debb3f00bbfa2a6d76f3abe9d197eefb3b4",
                    Optional.of("eeba3b4843c0b0995034ddf5fefbf6e61ad0d29c8448203f648c4a00397066a5")
                ),
                Arguments.of(
                    Short::class.java,
                    { Integer.valueOf(10).toShort() },
                    "2a709cf5b3fd1c874eb74937f933bac5e4cefa26fdb399a32939303b627b4b7d",
                    Optional.of("eeba3b4843c0b0995034ddf5fefbf6e61ad0d29c8448203f648c4a00397066a5")
                ),
                Arguments.of(
                    Integer::class.java,
                    { Integer.valueOf(10) },
                    "2719b104b28ab4c56f21aa222fe80072316fc35e1bb12959482395a292a06143",
                    Optional.of("eeba3b4843c0b0995034ddf5fefbf6e61ad0d29c8448203f648c4a00397066a5")
                ),
                Arguments.of(
                    Long::class.java,
                    { Integer.valueOf(10).toLong() },
                    "eeba3b4843c0b0995034ddf5fefbf6e61ad0d29c8448203f648c4a00397066a5",
                    Optional.empty<String>()
                ),
                Arguments.of(
                    Float::class.java,
                    { java.lang.Float.valueOf(10.0f) },
                    "a543eb24da968356ffb9975711a0b48e424e896b883cdd98eb2dc2c6516a2a5e",
                    Optional.of("70fb748e70c976827972c5a7e6a0a5d245a064482fceecacefcbdabc7ae800c8")
                ),
                Arguments.of(
                    Double::class.java,
                    { java.lang.Double.valueOf(10.0) },
                    "70fb748e70c976827972c5a7e6a0a5d245a064482fceecacefcbdabc7ae800c8",
                    Optional.empty<String>()
                ),
                Arguments.of(
                    String::class.java,
                    { "10" },
                    "24b014ca353b4654ad3a68c8b7943cb4b5493cde3667b4b25821cb9701bab250",
                    Optional.empty<String>()
                ),
                Arguments.of(
                    ByteBuffer::class.java,
                    { ByteBuffer.wrap("10".toByteArray(Charsets.UTF_8)) },
                    "24b014ca353b4654ad3a68c8b7943cb4b5493cde3667b4b25821cb9701bab250",
                    Optional.empty<String>()
                )
            ).stream()
        }
    }
}
