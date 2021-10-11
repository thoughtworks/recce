CREATE TABLE data_set_migration_record
(
    data_set_id   VARCHAR(255) NOT NULL,
    migration_key VARCHAR(255) NOT NULL,
    source_data   VARCHAR(1024),
    target_data   VARCHAR(1024),
    PRIMARY KEY (data_set_id, migration_key)
);
