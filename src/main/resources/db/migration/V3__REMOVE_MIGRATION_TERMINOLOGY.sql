ALTER TABLE dataset_migration_run
    RENAME TO reconciliation_run;
ALTER TABLE dataset_migration_record
    RENAME TO reconciliation_record;
ALTER TABLE reconciliation_record
    RENAME COLUMN migration_id TO reconciliation_run_id;