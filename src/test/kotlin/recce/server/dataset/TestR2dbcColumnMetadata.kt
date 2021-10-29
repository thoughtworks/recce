package recce.server.dataset

import io.r2dbc.spi.ColumnMetadata

class TestR2dbcColumnMetadata(private val name: String, private val javaType: Class<*>) : ColumnMetadata {
    override fun getName() = name
    override fun getJavaType() = javaType
}
