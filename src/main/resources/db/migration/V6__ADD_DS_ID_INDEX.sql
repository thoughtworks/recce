ALTER INDEX IF EXISTS data_set_migration_run_pkey
    RENAME TO reconciliation_run_pkey;
ALTER INDEX IF EXISTS data_set_migration_record_pkey
    RENAME TO reconciliation_record_pkey;
CREATE INDEX reconciliation_run_dataset_id ON reconciliation_run(dataset_id);
