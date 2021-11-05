ALTER TABLE reconciliation_run
    ADD COLUMN source_meta_cols VARCHAR(500);
ALTER TABLE reconciliation_run
    ADD COLUMN target_meta_cols VARCHAR(500);
