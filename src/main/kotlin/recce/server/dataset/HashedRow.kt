package recce.server.dataset

import io.r2dbc.spi.ColumnMetadata
import recce.server.recrun.ColMeta
import recce.server.recrun.DatasetMeta

data class HashedRow(
    val migrationKey: String,
    val hashedValue: String,
    private val columnMetadatas: Iterable<ColumnMetadata>
) {
    fun lazyMeta(): LazyDatasetMeta =
        { DatasetMeta(columnMetadatas.map { dbMeta -> ColMeta(dbMeta.name, dbMeta.javaType.simpleName) }) }
}

typealias LazyDatasetMeta = () -> DatasetMeta
