CREATE TABLE big_table
(
    id             INTEGER PRIMARY KEY NOT NULL,
    category       VARCHAR(100) NOT NULL,
    name           VARCHAR(255) NOT NULL UNIQUE,
    status         VARCHAR(20) NOT NULL
);

INSERT INTO big_table (
    id, category, name, status
)
SELECT
    i,
    md5(random()::text),
    md5(random()::text),
    left(md5(random()::text), 20)
from generate_series(50001, 150000) s(i);
