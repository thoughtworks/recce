CREATE TABLE TESTDATA (
    id int not null primary key,
    name varchar(255),
    value varchar(255)
);

INSERT INTO TESTDATA (id, name, value) VALUES (1, 'Test', 'User');
INSERT INTO TESTDATA (id, name, value) VALUES (2, 'Test2', 'User2');