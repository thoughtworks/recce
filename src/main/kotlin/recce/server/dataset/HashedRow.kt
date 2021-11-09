package recce.server.dataset

import com.google.common.hash.Hashing
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import recce.server.dataset.DataLoadDefinition.Companion.migrationKeyColumnName
import recce.server.recrun.ColMeta
import recce.server.recrun.DatasetMeta
import java.math.BigDecimal
import java.nio.ByteBuffer

data class HashedRow(val migrationKey: String, val hashedValue: String, private val rowMeta: RowMetadata) {
    companion object {
        @Suppress("UnstableApiUsage")
        fun fromRow(row: Row, meta: RowMetadata): HashedRow {
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
                    when {
                        colMeta.name.equals(migrationKeyColumnName, ignoreCase = true) -> trySetMigrationKey(col)
                        col == null -> hash.putString("${colMeta.javaType.simpleName}(NULL)", Charsets.UTF_8)
                        col is Boolean -> hash.putBoolean(col)
                        col is BigDecimal -> {
                            hash.putLong(col.unscaledValue().toLong())
                            hash.putInt(col.scale())
                        }
                        col is Byte -> hash.putByte(col)
                        col is Short -> hash.putShort(col)
                        col is Int -> hash.putInt(col)
                        col is Long -> hash.putLong(col)
                        col is Float -> hash.putFloat(col)
                        col is Double -> hash.putDouble(col)
                        col is String -> hash.putString(col, Charsets.UTF_8)
                        col is ByteBuffer -> hash.putBytes(col)
                        else -> throw IllegalArgumentException("Does not understand how to hash ${col.javaClass.name} for column [${colMeta.name}] at index [$i]")
                    }
                }

            return HashedRow(
                migrationKey ?: throw IllegalArgumentException("No column named $migrationKeyColumnName found in dataset"),
                hash.hash().toString(),
                meta
            )
        }
    }

    fun lazyMeta(): () -> DatasetMeta =
        { DatasetMeta(rowMeta.columnMetadatas.map { dbMeta -> ColMeta(dbMeta.name, dbMeta.javaType.simpleName) }) }
}
