package com.thoughtworks.recce.server.dataset

import com.google.common.hash.Hashing
import com.thoughtworks.recce.server.config.DataLoadDefinition.Companion.migrationKeyColumnName
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import java.math.BigDecimal
import java.nio.ByteBuffer

data class HashedRow(val migrationKey: String, val hashedValue: String)

@Suppress("UnstableApiUsage")
fun toHashedRow(row: Row, meta: RowMetadata): HashedRow {

    val migrationKey = row.get(migrationKeyColumnName, String::class.java)
        ?: throw IllegalArgumentException("Result rows do not have String column called [$migrationKeyColumnName]!")

    val hash = Hashing.sha256().newHasher()
    meta.columnNames
        .filter { !it.equals(migrationKeyColumnName) }
        .forEach {
            when (val col = row.get(it)) {
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
                else -> throw IllegalArgumentException("Does not understand how to hash ${col?.javaClass?.simpleName} for column [$it]")
            }
        }
    return HashedRow(migrationKey, hash.hash().toString())
}
