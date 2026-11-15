-- Run once on an existing database: mysql -u root -p < database/migrate_auth_users.sql
-- Adds user accounts and roles (CUSTOMER, SELLER, ADMIN).
-- Demo admin/seller accounts are created by the backend from environment variables
-- (see docs/LOCAL_SETUP.md); no passwords are stored in this file.
USE online_bookstore;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    role VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('CUSTOMER', 'SELLER', 'ADMIN'))
);
