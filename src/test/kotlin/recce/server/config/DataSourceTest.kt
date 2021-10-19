package recce.server.config

import jakarta.inject.Inject
import jakarta.inject.Named
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import javax.sql.DataSource

abstract class DataSourceTest {

    @Inject
    @field:Named("source")
    protected lateinit var sourceDataSource: DataSource

    @Inject
    @field:Named("target")
    protected lateinit var targetDataSource: DataSource

    protected object TestData : Table() {
        val name = varchar("name", 255)
        val value = varchar("value", 255)

        override val primaryKey = PrimaryKey(name)
    }

    protected val sourceDb: Database
        get() {
            return Database.connect(sourceDataSource)
        }

    protected val targetDb: Database
        get() {
            return Database.connect(targetDataSource)
        }

    @BeforeEach
    fun setup() {
        for (db in listOf(sourceDb, targetDb)) {
            transaction(db) {
                SchemaUtils.create(TestData)
            }
        }

        transaction(sourceDb) {
            insertUsers(0 until 3)
        }
        transaction(targetDb) {
            insertUsers(0 until 2)
            insertUsers(3 until 5)
        }
    }

    @AfterEach
    open fun tearDown() {
        for (db in listOf(sourceDb, targetDb)) {
            transaction(db) {
                SchemaUtils.drop(TestData)
            }
        }
    }

    private fun insertUsers(users: IntRange) {
        for (i in users) {
            TestData.insert {
                it[name] = "Test$i"
                it[value] = "User$i"
            }
        }
    }
}
