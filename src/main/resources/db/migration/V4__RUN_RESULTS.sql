ALTER TABLE reconciliation_run
    ADD COLUMN summary_source_only INTEGER;
ALTER TABLE reconciliation_run
    ADD COLUMN summary_target_only INTEGER;
ALTER TABLE reconciliation_run
    ADD COLUMN summary_both_matched INTEGER;
ALTER TABLE reconciliation_run
    ADD COLUMN summary_both_mismatched INTEGER;