package recce.server.dataset

import io.r2dbc.spi.ColumnMetadata
import io.r2dbc.spi.RowMetadata

class TestR2dbcColumnMetadata(private val name: String, private val javaType: Class<*>) : ColumnMetadata {
    override fun getName() = name
    override fun getJavaType() = javaType
}

class TestR2dbcRowMetadata(private val cols: List<TestR2dbcColumnMetadata>) : RowMetadata {
    override fun getColumnMetadata(index: Int) = cols[index]
    override fun getColumnMetadata(name: String) = cols.first { it.name == name }
    override fun getColumnMetadatas() = cols
    override fun getColumnNames() = cols.map { it.name }
}
