USE online_bookstore;

ALTER TABLE orders
    ADD COLUMN owner_session_id VARCHAR(128) NULL,
    ADD INDEX idx_orders_owner_session_id (owner_session_id);
