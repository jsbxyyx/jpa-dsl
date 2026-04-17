-- Schema for upsert integration tests.
-- Uses DROP + CREATE (not IF NOT EXISTS) so the UNIQUE constraint is always present.

DROP TABLE IF EXISTS t_user;
CREATE TABLE t_user (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    email    VARCHAR(200),
    age      INT,
    status   VARCHAR(50),
    CONSTRAINT uq_t_user_username UNIQUE (username)
);
