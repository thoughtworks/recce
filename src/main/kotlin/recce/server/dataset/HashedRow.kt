package recce.server.dataset

import io.r2dbc.spi.RowMetadata
import recce.server.recrun.ColMeta
import recce.server.recrun.DatasetMeta

data class HashedRow(val migrationKey: String, val hashedValue: String, private val rowMeta: RowMetadata) {
    fun lazyMeta(): LazyDatasetMeta =
        { DatasetMeta(rowMeta.columnMetadatas.map { dbMeta -> ColMeta(dbMeta.name, dbMeta.javaType.simpleName) }) }
}

typealias LazyDatasetMeta = () -> DatasetMeta
