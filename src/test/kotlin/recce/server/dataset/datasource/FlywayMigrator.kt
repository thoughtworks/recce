package recce.server.dataset.datasource

import io.micronaut.transaction.annotation.TransactionalAdvice
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import java.nio.file.Files
import java.nio.file.Path
import javax.sql.DataSource
import javax.transaction.Transactional

@Singleton
open class FlywayMigrator {

    private val createTable = """
            CREATE TABLE TestData (
                name VARCHAR(255) PRIMARY KEY NOT NULL,
                val VARCHAR(255) NOT NULL
            );
    """.trimMargin()

    private val insertUser: (Int) -> String = { i ->
        """
            INSERT INTO TestData (name, val) 
            VALUES ('Test$i', 'User$i');
        """.trimIndent()
    }

    @Inject
    @field:Named("source-h2-sync")
    lateinit var sourceDataSource: DataSource

    @Inject
    @field:Named("target-h2-sync")
    lateinit var targetDataSource: DataSource

    @Transactional
    @TransactionalAdvice(transactionManager = "source-h2-sync")
    open fun cleanMigrateSource(
        tempDir: Path,
        sql: String = createTable + (0..2).joinToString("\n", transform = insertUser)
    ) {
        flywayCleanMigrate(tempDir, sql) { it.dataSource(sourceDataSource) }
    }

    @Transactional
    @TransactionalAdvice(transactionManager = "target-h2-sync")
    open fun cleanMigrateTarget(
        tempDir: Path,
        sql: String = createTable + ((0..1) + (3..4)).joinToString("\n", transform = insertUser)
    ) {
        flywayCleanMigrate(tempDir, sql) { it.dataSource(targetDataSource) }
    }
}

fun flywayCleanMigrate(temporaryDir: Path, sql: String, db: DbDescriptor) =
    flywayCleanMigrate(temporaryDir, sql) { it.dataSource(db.jdbcUrl, db.username, db.password) }

private fun flywayCleanMigrate(temporaryDir: Path, sql: String, configureHook: (FluentConfiguration) -> Unit) {
    val migrationsLoc = Files.createTempDirectory(temporaryDir, "scenario-")
    Files.writeString(migrationsLoc.resolve("V1__SETUP_TEST.sql"), sql)
    Flyway.configure()
        .locations("filesystem:$migrationsLoc")
        .cleanOnValidationError(true)
        .also(configureHook)
        .load()
        .migrate()
}

data class DbDescriptor(val jdbcUrl: String, val username: String, val password: String)
