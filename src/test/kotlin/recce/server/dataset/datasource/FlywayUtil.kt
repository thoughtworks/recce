package recce.server.dataset.datasource

import org.flywaydb.core.Flyway
import java.nio.file.Files
import java.nio.file.Path

data class DbDescriptor(val jdbcUrl: String, val username: String, val password: String)

fun flywayCleanMigrate(temporaryDir: Path, sql: String, db: DbDescriptor) {

    val migrationsLoc = Files.createTempDirectory(temporaryDir, "scenario-")
    Files.writeString(migrationsLoc.resolve("V1__SETUP_TEST.sql"), sql)
    Flyway.configure()
        .dataSource(db.jdbcUrl, db.username, db.password)
        .locations("filesystem:$migrationsLoc")
        .cleanOnValidationError(true)
        .load()
        .migrate()
}
