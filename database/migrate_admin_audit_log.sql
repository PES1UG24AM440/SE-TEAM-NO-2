-- Run once on an existing database, AFTER database/migrate_auth_users.sql:
--   mysql -u root -p < database/migrate_admin_audit_log.sql
-- Issue #73: audit log of administrator actions.
USE online_bookstore;

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor_user_id BIGINT NOT NULL,
    actor_email VARCHAR(254) NOT NULL,
    action VARCHAR(50) NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    details VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_audit_log_created (created_at),
    INDEX idx_audit_log_target (target_type, target_id)
);
