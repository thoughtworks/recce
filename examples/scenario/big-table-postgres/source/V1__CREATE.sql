CREATE TABLE big_table
(
    id             SERIAL PRIMARY KEY NOT NULL,
    category       VARCHAR(100) NOT NULL,
    name           VARCHAR(255) NOT NULL UNIQUE,
    status         VARCHAR(20) NOT NULL
);

INSERT INTO big_table (
    category, name, status
)
SELECT
    md5(random()::text),
    md5(random()::text),
    left(md5(random()::text), 4)
from generate_series(1, 100000) s(i);
