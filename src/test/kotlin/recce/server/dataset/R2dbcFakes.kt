package recce.server.dataset

import io.r2dbc.spi.ColumnMetadata
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata

class FakeColumnMetadata(private val name: String, private val javaType: Class<*>) : ColumnMetadata {
    override fun getName() = name
    override fun getJavaType() = javaType
}

class FakeRowMetadata(private val cols: List<ColumnMetadata>) : RowMetadata {
    override fun getColumnMetadata(index: Int) = cols[index]
    override fun getColumnMetadata(name: String) = cols.first { it.name == name }
    override fun getColumnMetadatas() = cols
    override fun getColumnNames() = cols.map { it.name }
}

class FakeRow(private val values: List<Pair<String, Any?>>) : Row {
    private val valuesByColName = values.toMap()

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(index: Int, type: Class<T>): T? {
        if (type != Object::class.java) throw IllegalArgumentException("Only support generic Object type returns")
        return values[index].second as T
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(name: String, type: Class<T>): T? {
        if (type != Object::class.java) throw IllegalArgumentException("Only support generic Object type returns")
        return valuesByColName[name] as T
    }
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
        if (hasMigrationKey && cols.size == 1 || cols.size == 0) throw IllegalArgumentException("Populate column metadata first!")
        if (values.size != cols.size) throw IllegalArgumentException("Incorrect number of row values, expected ${cols.size}")

        rowValues = values.mapIndexed { index, value -> cols[index].name to value }

        return this
    }

    fun build(): Pair<Row, RowMetadata> {
        return buildRow() to buildMeta()
    }

    fun buildRow(): Row {
        return FakeRow(rowValues)
    }

    fun buildMeta(): RowMetadata {
        return FakeRowMetadata(cols)
    }
}
