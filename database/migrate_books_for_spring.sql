-- Back up online_bookstore before applying this one-time migration.
-- Safe to rerun if an earlier attempt applied only part of the migration.
USE online_bookstore;

SET @ddl = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.referential_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'order_items'
          AND constraint_name = 'fk_order_items_book'
    ),
    'ALTER TABLE order_items DROP FOREIGN KEY fk_order_items_book',
    'SELECT 1'
);
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF(
    (SELECT data_type
     FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'books'
       AND column_name = 'id') <> 'bigint',
    'ALTER TABLE books MODIFY id BIGINT NOT NULL AUTO_INCREMENT',
    'SELECT 1'
);
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'books'
          AND column_name = 'category'
    ),
    'ALTER TABLE books ADD COLUMN category VARCHAR(100) NOT NULL DEFAULT ''Uncategorized''',
    'SELECT 1'
);
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'books'
          AND column_name = 'description'
    ),
    'ALTER TABLE books ADD COLUMN description TEXT NULL',
    'SELECT 1'
);
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF(
    (SELECT data_type
     FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'order_items'
       AND column_name = 'book_id') <> 'bigint',
    'ALTER TABLE order_items MODIFY book_id BIGINT NOT NULL',
    'SELECT 1'
);
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1
        FROM information_schema.referential_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'order_items'
          AND constraint_name = 'fk_order_items_book'
    ),
    'ALTER TABLE order_items ADD CONSTRAINT fk_order_items_book FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE RESTRICT',
    'SELECT 1'
);
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;
