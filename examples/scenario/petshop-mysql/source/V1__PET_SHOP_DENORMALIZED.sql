CREATE TABLE pet
(
    id             INTEGER PRIMARY KEY NOT NULL,
    category       VARCHAR(100) NOT NULL,
    name           VARCHAR(255) NOT NULL UNIQUE,
    status         VARCHAR(20) NOT NULL
);

CREATE TABLE order_status
(
    id             INTEGER PRIMARY KEY NOT NULL,
    pet_name       VARCHAR(255) NOT NULL,
    ordered_at     TIMESTAMP,
    status         INTEGER NOT NULL,
    complete       BOOLEAN
);

INSERT INTO pet (id, category, name, status) VALUES (1, 'Cat', 'Niobe', 'sold');
INSERT INTO pet (id, category, name, status) VALUES (2, 'Dog', 'Rex', 'available');
INSERT INTO pet (id, category, name, status) VALUES (3, 'Dog', 'Fido', 'pending');

INSERT INTO order_status (id, pet_name, ordered_at, status, complete) VALUES (1, 'Niobe', '2012-10-01 13:00:10', 3, TRUE);
INSERT INTO order_status (id, pet_name, ordered_at, status, complete) VALUES (2, 'Fido', '2021-01-11 09:30:10', 1, FALSE);
INSERT INTO order_status (id, pet_name, ordered_at, status, complete) VALUES (3, 'Missing', '2021-01-11 09:30:10', 2, FALSE);
