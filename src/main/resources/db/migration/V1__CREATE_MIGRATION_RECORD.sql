CREATE TABLE data_set_migration_run
(
    id             SERIAL PRIMARY KEY,
    data_set_id    VARCHAR(255) NOT NULL,
    created_time   TIMESTAMP    NOT NULL,
    updated_time   TIMESTAMP    NOT NULL,
    completed_time TIMESTAMP
);

CREATE TABLE data_set_migration_record
(
    migration_id  INTEGER      NOT NULL,
    migration_key VARCHAR(255) NOT NULL,
    source_data   VARCHAR(1024),
    target_data   VARCHAR(1024),
    PRIMARY KEY (migration_id, migration_key),
    CONSTRAINT fk_migration_id FOREIGN KEY (migration_id) REFERENCES data_set_migration_run (id) ON DELETE CASCADE
);
