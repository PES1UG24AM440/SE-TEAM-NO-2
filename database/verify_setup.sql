-- Quick health check for the online_bookstore schema.
--   mysql -u bookstore_app -p < database/verify_setup.sql
-- Every row in the first result should say OK.
SELECT t.table_name AS required_table,
       IF(i.table_name IS NULL, 'MISSING - run database/schema.sql', 'OK') AS status
FROM (SELECT 'books' AS table_name UNION ALL SELECT 'orders' UNION ALL SELECT 'order_items'
      UNION ALL SELECT 'inventory_movements' UNION ALL SELECT 'idempotency_keys' UNION ALL SELECT 'cart_lines') t
LEFT JOIN information_schema.tables i
       ON i.table_schema = 'online_bookstore' AND i.table_name = t.table_name;

SELECT IF(COUNT(*) = 0, 'MISSING - run database/migrate_order_session_ownership.sql', 'OK') AS orders_owner_session_id
FROM information_schema.columns
WHERE table_schema = 'online_bookstore' AND table_name = 'orders' AND column_name = 'owner_session_id';

SELECT COUNT(*) AS books_in_catalogue,
       SUM(stock > 0) AS books_in_stock,
       COUNT(DISTINCT category) AS categories
FROM online_bookstore.books;

SELECT CURRENT_USER() AS connected_as, VERSION() AS mysql_version;
