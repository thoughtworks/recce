package recce.server.dataset

import io.r2dbc.spi.ColumnMetadata
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import io.r2dbc.spi.Type

data class FakeColumnMetadata(private val name: String, private val javaType: Class<*>) : ColumnMetadata {
    override fun getName() = name
    override fun getJavaType() = type.javaType
    override fun getType() = FakeType(javaType)
}

data class FakeType(private val javaType: Class<*>) : Type {
    override fun getJavaType() = javaType
    override fun getName(): String = javaType.name
}

class FakeRowMetadata(private val cols: List<ColumnMetadata>) : RowMetadata {
    override fun getColumnMetadata(index: Int) = cols[index]
    override fun getColumnMetadata(name: String) = cols.first { it.name == name }
    override fun getColumnMetadatas() = cols
}

class FakeRow(private val meta: RowMetadata, private val values: List<Pair<String, Any?>>) : Row {
    private val valuesByColName = values.toMap()

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(index: Int, type: Class<T>): T? {
        require(type == Object::class.java) { "Only support generic Object type returns" }
        return values[index].second as T
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(name: String, type: Class<T>): T? {
        require(type == Object::class.java) { "Only support generic Object type returns" }
        return valuesByColName[name] as T
    }

    override fun getMetadata() = meta
}

class R2dbcFakeBuilder {
    private val cols = mutableListOf<ColumnMetadata>(
        FakeColumnMetadata(DataLoadDefinition.migrationKeyColumnName, String::class.java)
    )
    private var hasMigrationKey: Boolean = true

    private lateinit var rowValues: List<Pair<String, Any?>>

    fun noMigrationKey(): R2dbcFakeBuilder {
        hasMigrationKey = false
        cols.clear()
        return this
    }

    fun withCol(col: ColumnMetadata): R2dbcFakeBuilder {
        cols.add(col)
        return this
    }

    fun withCol(name: String, javaType: Class<*>): R2dbcFakeBuilder {
        return withCol(FakeColumnMetadata(name, javaType))
    }

    fun withRowValues(vararg values: Any?): R2dbcFakeBuilder {
        require(cols.size > if (hasMigrationKey) 1 else 0) { "Populate column metadata first!" }
        require(cols.size == values.size) { "Incorrect number of row values, expected ${cols.size}" }

        rowValues = values.mapIndexed { index, value -> cols[index].name to value }

        return this
    }

    fun build(): Row {
        return FakeRow(buildMeta(), rowValues)
    }

    fun buildMeta(): RowMetadata {
        return FakeRowMetadata(cols)
    }

    fun buildColMetas(): Iterable<ColumnMetadata> {
        return cols
    }
}
