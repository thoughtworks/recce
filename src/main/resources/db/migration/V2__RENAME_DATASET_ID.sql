ALTER TABLE data_set_migration_run
    RENAME COLUMN data_set_id TO dataset_id;
ALTER TABLE data_set_migration_run
    RENAME TO dataset_migration_run;
ALTER TABLE data_set_migration_record
    RENAME TO dataset_migration_record;