CREATE TABLE data_set_migration_run
(
    id             INTEGER PRIMARY KEY AUTO_INCREMENT NOT NULL,
    data_set_id    VARCHAR(255)                       NOT NULL,
    created_time   TIMESTAMP                          NOT NULL,
    updated_time   TIMESTAMP                          NOT NULL,
    completed_time TIMESTAMP
);

CREATE TABLE data_set_migration_record
(
    migration_id  INTEGER      NOT NULL,
    migration_key VARCHAR(255) NOT NULL,
    source_data   VARCHAR(1024),
    target_data   VARCHAR(1024),
    PRIMARY KEY (migration_id, migration_key),
    FOREIGN KEY (migration_id) REFERENCES data_set_migration_run (id)
);

INSERT INTO data_set_migration_run (data_set_id, created_time, updated_time, completed_time)
VALUES ('test-dataset', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP());

INSERT INTO data_set_migration_record (migration_id, migration_key, source_data, target_data)
VALUES (1, 'Key', 'SourceHash', 'TargetHash');
INSERT INTO data_set_migration_record (migration_id, migration_key, source_data, target_data)
VALUES (1, 'Key2', 'SourceHash2', 'TargetHash2');
INSERT INTO data_set_migration_record (migration_id, migration_key, source_data, target_data)
VALUES (1, 'Key3', 'SourceHash3', 'TargetHash3');