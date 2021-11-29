package recce.server.dataset

import com.google.common.hash.Hasher
import com.google.common.hash.Hashing
import io.r2dbc.spi.ColumnMetadata
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import recce.server.dataset.DataLoadDefinition.Companion.migrationKeyColumnName
import java.math.BigDecimal
import java.nio.ByteBuffer

@Suppress("UnstableApiUsage")
enum class HashingStrategy {
    TypeLenient {
        override fun hashCol(hasher: Hasher, index: Int, colMeta: ColumnMetadata, col: Any?) {
            when (col) {
                null -> {}
                is Boolean -> hasher.putLong(if (col) 1 else 0)
                is Byte -> hasher.putLong(col.toLong())
                is Short -> hasher.putLong(col.toLong())
                is Int -> hasher.putLong(col.toLong())
                is Long -> hasher.putLong(col)
                is Float -> hasher.putDouble(col.toDouble())
                is Double -> hasher.putDouble(col)
                is BigDecimal -> {
                    hasher.putLong(col.unscaledValue().toLong())
                    hasher.putInt(col.scale())
                }
                is String -> hasher.putString(col, Charsets.UTF_8)
                is ByteBuffer -> hasher.putBytes(col)
                else -> throw IllegalArgumentException("$this hasher does not understand how to hash ${col.javaClass.name} for column [${colMeta.name}] at index [$index]")
            }
        }
    },
    TypeStrict {
        override fun hashCol(hasher: Hasher, index: Int, colMeta: ColumnMetadata, col: Any?) {
            when (col) {
                null -> hasher.putString("${colMeta.javaType.simpleName}(NULL)", Charsets.UTF_8)
                is Boolean -> hasher.putBoolean(col)
                is Byte -> hasher.putByte(col)
                is Short -> hasher.putShort(col)
                is Int -> hasher.putInt(col)
                is Long -> hasher.putLong(col)
                is Float -> hasher.putFloat(col)
                is Double -> hasher.putDouble(col)
                is BigDecimal -> {
                    hasher.putLong(col.unscaledValue().toLong())
                    hasher.putInt(col.scale())
                }
                is String -> hasher.putString(col, Charsets.UTF_8)
                is ByteBuffer -> hasher.putBytes(col)
                else -> throw IllegalArgumentException("$this hasher does not understand how to hash ${col.javaClass.name} for column [${colMeta.name}] at index [$index]")
            }
        }
    };

    protected abstract fun hashCol(hasher: Hasher, index: Int, colMeta: ColumnMetadata, col: Any?)

    fun hash(row: Row, meta: RowMetadata): HashedRow {
        var migrationKey: String? = null
        val hasher = Hashing.sha256().newHasher()

        fun trySetMigrationKey(col: Any?) {
            when {
                col == null -> throw IllegalArgumentException("$migrationKeyColumnName has null value somewhere in dataset")
                migrationKey != null -> throw IllegalArgumentException("More than one column named $migrationKeyColumnName found in dataset")
                else -> migrationKey = col.toString()
            }
        }

        meta.columnMetadatasSanitized()
            .forEachIndexed { i, colMeta ->
                if (colMeta.name.equals(migrationKeyColumnName, ignoreCase = true)) {
                    trySetMigrationKey(row[i])
                } else {
                    hashCol(hasher, i, colMeta, row[i])
                }

                hasher.putChar(HASH_FIELD_SEPARATOR)
            }

        return HashedRow(
            migrationKey ?: throw IllegalArgumentException("No column named $migrationKeyColumnName found in dataset"),
            hasher.hash().toString(),
            meta.columnMetadatasSanitized()
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

/**
 * Hack needed for MS SQL Server R2DBC driver < 0.8.8. See https://github.com/r2dbc/r2dbc-mssql/issues/235
 */
fun RowMetadata.columnMetadatasSanitized(): Iterable<ColumnMetadata> {
    if (columnMetadatas is List<*>) {
        @Suppress("UNCHECKED_CAST")
        return (columnMetadatas as List<ColumnMetadata>).dropLastWhile { it.name == "ROWSTAT" }
    } else {
        throw UnsupportedOperationException("Cannot process non-list R2DBC metadata, type was ${columnMetadatas.javaClass.name}")
    }
}
