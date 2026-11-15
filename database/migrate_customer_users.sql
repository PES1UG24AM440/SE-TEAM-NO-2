USE online_bookstore;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(60) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
    CONSTRAINT uq_users_email UNIQUE (email)
);

SET @role_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'users'
      AND column_name = 'role'
);
SET @role_migration = IF(
    @role_column_exists = 0,
    'ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT ''CUSTOMER''',
    'SELECT 1'
);
PREPARE role_migration_statement FROM @role_migration;
EXECUTE role_migration_statement;
DEALLOCATE PREPARE role_migration_statement;
