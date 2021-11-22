package recce.server.dataset

import com.google.common.hash.Hasher
import com.google.common.hash.Hashing
import io.r2dbc.spi.ColumnMetadata
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import recce.server.dataset.DataLoadDefinition.Companion.migrationKeyColumnName
import recce.server.recrun.ColMeta
import recce.server.recrun.DatasetMeta
import java.math.BigDecimal
import java.nio.ByteBuffer

@Suppress("UnstableApiUsage")
enum class HashingStrategy {
    TypeLenient {
        override fun put(hash: Hasher, col: Any?, colMeta: ColumnMetadata, index: Int) {
            when (col) {
                null -> hash.putString("${colMeta.javaType.simpleName}(NULL)", Charsets.UTF_8)
                is Boolean -> hash.putBoolean(col)
                is Byte -> hash.putLong(col.toLong())
                is Short -> hash.putLong(col.toLong())
                is Int -> hash.putLong(col.toLong())
                is Long -> hash.putLong(col)
                is Float -> hash.putDouble(col.toDouble())
                is Double -> hash.putDouble(col)
                is BigDecimal -> {
                    hash.putLong(col.unscaledValue().toLong())
                    hash.putInt(col.scale())
                }
                is String -> hash.putString(col, Charsets.UTF_8)
                is ByteBuffer -> hash.putBytes(col)
                else -> throw IllegalArgumentException("Does not understand how to hash ${col.javaClass.name} for column [${colMeta.name}] at index [$index]")
            }
        }
    },
    TypeStrict {
        override fun put(hash: Hasher, col: Any?, colMeta: ColumnMetadata, index: Int) {
            when (col) {
                null -> hash.putString("${colMeta.javaType.simpleName}(NULL)", Charsets.UTF_8)
                is Boolean -> hash.putBoolean(col)
                is Byte -> hash.putByte(col)
                is Short -> hash.putShort(col)
                is Int -> hash.putInt(col)
                is Long -> hash.putLong(col)
                is Float -> hash.putFloat(col)
                is Double -> hash.putDouble(col)
                is BigDecimal -> {
                    hash.putLong(col.unscaledValue().toLong())
                    hash.putInt(col.scale())
                }
                is String -> hash.putString(col, Charsets.UTF_8)
                is ByteBuffer -> hash.putBytes(col)
                else -> throw IllegalArgumentException("Does not understand how to hash ${col.javaClass.name} for column [${colMeta.name}] at index [$index]")
            }
        }
    };

    @Suppress("UnstableApiUsage")
    abstract fun put(hash: Hasher, col: Any?, colMeta: ColumnMetadata, index: Int)

    fun build(row: Row, meta: RowMetadata): HashedRow {
        var migrationKey: String? = null
        val hash = Hashing.sha256().newHasher()

        fun trySetMigrationKey(col: Any?) {
            when {
                col == null -> throw IllegalArgumentException("$migrationKeyColumnName has null value somewhere in dataset")
                migrationKey != null -> throw IllegalArgumentException("More than one column named $migrationKeyColumnName found in dataset")
                else -> migrationKey = col.toString()
            }
        }

        meta.columnMetadatas
            .forEachIndexed { i, colMeta ->
                val col = row[i]
                if (colMeta.name.equals(migrationKeyColumnName, ignoreCase = true)) {
                    trySetMigrationKey(col)
                } else {
                    put(hash, col, colMeta, i)
                }

                hash.putChar(HASH_FIELD_SEPARATOR)
            }

        return HashedRow(
            migrationKey ?: throw IllegalArgumentException("No column named $migrationKeyColumnName found in dataset"),
            hash.hash().toString(),
            meta
        )
    }

    companion object {
        /**
         * We need a value to add to the hash to delineate each column value that is extremely unlikely to be included
         * in the actual values from the database. This is to ensure that adjacent column values, when concatenated into
         * the hashing function, lead to different hashes, even if when concatenated as binary would be equivalent.
         *
         * There is no perfect value to use here; but we should avoid using something commonly represented the same way
         * in binary, e.g the NUL character or a zero
         */
        private const val HASH_FIELD_SEPARATOR = '\u2029'
    }
}

data class HashedRow(val migrationKey: String, val hashedValue: String, private val rowMeta: RowMetadata) {
    fun lazyMeta(): LazyDatasetMeta =
        { DatasetMeta(rowMeta.columnMetadatas.map { dbMeta -> ColMeta(dbMeta.name, dbMeta.javaType.simpleName) }) }
}

typealias LazyDatasetMeta = () -> DatasetMeta
