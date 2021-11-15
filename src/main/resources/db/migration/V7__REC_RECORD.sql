DROP TABLE IF EXISTS reconciliation_record;
CREATE TABLE reconciliation_record
(
    id                    SERIAL PRIMARY KEY,
    reconciliation_run_id INTEGER      NOT NULL
        CONSTRAINT fk_reconciliation_run_id REFERENCES reconciliation_run ON DELETE CASCADE,
    migration_key         VARCHAR(255) NOT NULL,
    source_data           VARCHAR(1024),
    target_data           VARCHAR(1024),
    CONSTRAINT reconciliation_record_uniq UNIQUE (reconciliation_run_id, migration_key)
);
