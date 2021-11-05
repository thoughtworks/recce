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

            val migrationKey = row.get(migrationKeyColumnName)?.toString()
                ?: throw IllegalArgumentException("$migrationKeyColumnName has null value somewhere in data set")

            val hash = Hashing.sha256().newHasher()
            meta.columnMetadatas
                .filter { !it.name.equals(migrationKeyColumnName, ignoreCase = true) }
                .forEach { colMeta ->
                    when (val col = row.get(colMeta.name)) {
                        null -> hash.putString("${colMeta.javaType.simpleName}(NULL)", Charsets.UTF_8)
                        is Boolean -> hash.putBoolean(col)
                        is BigDecimal -> {
                            hash.putLong(col.unscaledValue().toLong())
                            hash.putInt(col.scale())
                        }
                        is Short -> hash.putShort(col)
                        is Int -> hash.putInt(col)
                        is Long -> hash.putLong(col)
                        is Float -> hash.putFloat(col)
                        is Double -> hash.putDouble(col)
                        is String -> hash.putString(col, Charsets.UTF_8)
                        is ByteBuffer -> hash.putBytes(col)
                        else -> throw IllegalArgumentException("Does not understand how to hash ${col.javaClass.simpleName} for column [${colMeta.name}]")
                    }
                }
            return HashedRow(migrationKey, hash.hash().toString(), meta)
        }
    }

    fun lazyMeta(): () -> DatasetMeta =
        { DatasetMeta(rowMeta.columnMetadatas.map { dbMeta -> ColMeta(dbMeta.name, dbMeta.javaType.simpleName) }) }
}
