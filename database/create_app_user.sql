-- Creates a dedicated MySQL account for the Spring Boot backend.
-- Run once as an administrator AFTER schema.sql, for example:
--   mysql -u root -p < database/create_app_user.sql
-- Then start the backend with DB_USERNAME=bookstore_app and DB_PASSWORD=<the password below>.
-- Change the password for anything other than a local development machine.

CREATE USER IF NOT EXISTS 'bookstore_app'@'localhost' IDENTIFIED BY 'Bookstore@123';
CREATE USER IF NOT EXISTS 'bookstore_app'@'127.0.0.1' IDENTIFIED BY 'Bookstore@123';

-- The app only needs data access; schema changes are applied by hand from database/*.sql.
GRANT SELECT, INSERT, UPDATE, DELETE ON online_bookstore.* TO 'bookstore_app'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON online_bookstore.* TO 'bookstore_app'@'127.0.0.1';
FLUSH PRIVILEGES;
