CREATE TABLE category
(
    id             INTEGER PRIMARY KEY NOT NULL,
    name           VARCHAR(255) NOT NULL
);

CREATE TABLE pet
(
    id             INTEGER PRIMARY KEY NOT NULL,
    category_id    INTEGER NOT NULL REFERENCES category(id),
    name           VARCHAR(255) NOT NULL,
    status         VARCHAR(20) NOT NULL
);

CREATE TABLE order_status
(
    id             INTEGER PRIMARY KEY NOT NULL,
    pet_id         INTEGER NOT NULL REFERENCES pet(id),
    ordered_at     TIMESTAMP,
    status         VARCHAR(10) NOT NULL,
    complete       BOOLEAN
);

INSERT INTO category (id, name) VALUES (1, 'Cat');
INSERT INTO category (id, name) VALUES (2, 'Dog');

INSERT INTO pet (id, category_id, name, status) VALUES (1, 1, 'Niobe', 'sold');
INSERT INTO pet (id, category_id, name, status) VALUES (2, 2, 'Rex', 'available');
INSERT INTO pet (id, category_id, name, status) VALUES (3, 2, 'Fido', 'pending');

INSERT INTO order_status (id, pet_id, ordered_at, status, complete) VALUES (1, 1, '2012-10-01 13:00:10', 'delivered', TRUE);
INSERT INTO order_status (id, pet_id, ordered_at, status, complete) VALUES (2, 3, '2021-01-11 09:30:10', 'placed', FALSE);
