CREATE TABLE IF NOT EXISTS t_user (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    email    VARCHAR(200),
    age      INT,
    status   VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS t_order (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(50) NOT NULL,
    amount   DECIMAL(10, 2) NOT NULL,
    status   VARCHAR(50),
    user_id  BIGINT
);

CREATE TABLE IF NOT EXISTS t_audit_user (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(100) NOT NULL,
    status     VARCHAR(50),
    is_deleted INT DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
