ALTER TABLE reconciliation_run
    ADD COLUMN status VARCHAR(20);

-- noinspection SqlWithoutWhere
UPDATE reconciliation_run r
SET status = (
    SELECT CASE WHEN completed_time IS NOT NULL THEN 'Successful' ELSE 'Failed' END
    FROM reconciliation_run
    WHERE id = r.id
);

ALTER TABLE reconciliation_run ALTER status SET NOT NULL;
