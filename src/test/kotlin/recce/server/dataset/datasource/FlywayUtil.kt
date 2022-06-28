package recce.server.dataset.datasource

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import java.nio.file.Files
import java.nio.file.Path
import javax.sql.DataSource

fun flywayCleanMigrate(temporaryDir: Path, sql: String, db: DbDescriptor) =
    flywayCleanMigrate(temporaryDir, sql) { it.dataSource(db.jdbcUrl, db.username, db.password) }

fun flywayCleanMigrate(temporaryDir: Path, sql: String, db: DataSource) =
    flywayCleanMigrate(temporaryDir, sql) { it.dataSource(db) }

fun flywayCleanMigrate(temporaryDir: Path, sql: String, configureHook: (FluentConfiguration) -> Unit) {
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
