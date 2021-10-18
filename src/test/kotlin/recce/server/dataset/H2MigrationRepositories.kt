package recce.server.dataset

import io.micronaut.context.annotation.Replaces
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository

/**
 * Allow running of repository tests against H2 Database for speed. To re-evaluate later if we would prefer to use
 * slower TestContainers startups
 */
@Replaces(MigrationRunRepository::class)
@R2dbcRepository(dialect = Dialect.H2)
interface H2MigrationRunRepository : MigrationRunRepository

/**
 * Allow running of repository tests against H2 Database for speed. To re-evaluate later if we would prefer to use
 * slower TestContainers startups
 */
@Replaces(MigrationRecordRepository::class)
@R2dbcRepository(dialect = Dialect.H2)
interface H2MigrationRecordRepository : MigrationRecordRepository
