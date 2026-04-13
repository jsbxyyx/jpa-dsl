INSERT INTO t_user (username, email, age, status) VALUES ('alice', 'alice@example.com', 30, 'ACTIVE');
INSERT INTO t_user (username, email, age, status) VALUES ('bob', 'bob@example.com', 25, 'INACTIVE');
INSERT INTO t_user (username, email, age, status) VALUES ('charlie', NULL, 40, 'ACTIVE');

INSERT INTO t_order (order_no, amount, status, user_id) SELECT 'ORD-001', 100.00, 'PAID', id FROM t_user WHERE username = 'alice';
INSERT INTO t_order (order_no, amount, status, user_id) SELECT 'ORD-002', 250.00, 'PENDING', id FROM t_user WHERE username = 'alice';
INSERT INTO t_order (order_no, amount, status, user_id) SELECT 'ORD-003', 50.00, 'PAID', id FROM t_user WHERE username = 'bob';
