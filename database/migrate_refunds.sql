-- Run once on an existing database:
--   mysql -u root -p < database/migrate_refunds.sql
-- Issue #71: when each order was placed (for the cancellation window) and one refund per cancelled order.
-- Existing orders get created_at = the time this script runs.
USE online_bookstore;

ALTER TABLE orders
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS refunds (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id VARCHAR(36) NOT NULL,
    order_total DECIMAL(10,2) NOT NULL,
    shipping_withheld DECIMAL(10,2) NOT NULL,
    cancellation_fee DECIMAL(10,2) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    refund_type VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- One refund per order: a repeated cancellation can never create a second refund.
    CONSTRAINT uq_refunds_order UNIQUE (order_id),
    CONSTRAINT fk_refunds_order
        FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT,
    CONSTRAINT ck_refunds_amount_nonnegative CHECK (amount >= 0),
    CONSTRAINT ck_refunds_amount_not_above_total CHECK (amount <= order_total),
    CONSTRAINT ck_refunds_type CHECK (refund_type IN ('FULL', 'PARTIAL')),
    CONSTRAINT ck_refunds_status CHECK (status IN ('REFUNDED'))
);
