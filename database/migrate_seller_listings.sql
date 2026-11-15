-- Run once on an existing database, AFTER database/migrate_auth_users.sql:
--   mysql -u root -p < database/migrate_seller_listings.sql
-- Issue #72: listings belong to a seller and can be deactivated instead of deleted.
-- Existing books keep seller_id = NULL (store-owned) and stay active.
USE online_bookstore;

ALTER TABLE books
    ADD COLUMN seller_id BIGINT NULL,
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD INDEX idx_books_seller_id (seller_id),
    ADD CONSTRAINT fk_books_seller FOREIGN KEY (seller_id) REFERENCES users(id) ON DELETE RESTRICT;
