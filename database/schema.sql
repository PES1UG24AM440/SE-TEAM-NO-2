CREATE DATABASE IF NOT EXISTS online_bookstore
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE online_bookstore;

CREATE TABLE IF NOT EXISTS books (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(200) NOT NULL,
  author VARCHAR(150) NOT NULL,
  category VARCHAR(100) NOT NULL,
  description TEXT,
  price DECIMAL(10,2) NOT NULL,
  stock INT NOT NULL DEFAULT 0,
  CONSTRAINT chk_books_price CHECK (price >= 0),
  CONSTRAINT chk_books_stock CHECK (stock >= 0)
);

CREATE TABLE IF NOT EXISTS customer_orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_name VARCHAR(120) NOT NULL,
  customer_email VARCHAR(254) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'CONFIRMED',
  total DECIMAL(12,2) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT chk_order_total CHECK (total >= 0)
);

CREATE TABLE IF NOT EXISTS order_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  book_id BIGINT NOT NULL,
  book_title VARCHAR(200) NOT NULL,
  quantity INT NOT NULL,
  unit_price DECIMAL(10,2) NOT NULL,
  line_total DECIMAL(12,2) NOT NULL,
  CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES customer_orders(id) ON DELETE CASCADE,
  CONSTRAINT fk_order_items_book FOREIGN KEY (book_id) REFERENCES books(id),
  CONSTRAINT chk_order_item_quantity CHECK (quantity > 0),
  CONSTRAINT chk_order_item_price CHECK (unit_price >= 0 AND line_total >= 0),
  INDEX idx_order_items_order_id (order_id),
  INDEX idx_order_items_book_id (book_id)
);

INSERT INTO books (title, author, category, description, price, stock)
SELECT 'Clean Code', 'Robert C. Martin', 'Programming', 'A guide to writing maintainable code.', 450.00, 12
WHERE NOT EXISTS (SELECT 1 FROM books WHERE title = 'Clean Code');

INSERT INTO books (title, author, category, description, price, stock)
SELECT 'The Pragmatic Programmer', 'Andrew Hunt', 'Programming', 'Practical advice for software developers.', 550.00, 8
WHERE NOT EXISTS (SELECT 1 FROM books WHERE title = 'The Pragmatic Programmer');

INSERT INTO books (title, author, category, description, price, stock)
SELECT 'Atomic Habits', 'James Clear', 'Self-help', 'A book about building lasting habits.', 399.00, 15
WHERE NOT EXISTS (SELECT 1 FROM books WHERE title = 'Atomic Habits');

INSERT INTO books (title, author, category, description, price, stock)
SELECT 'The Alchemist', 'Paulo Coelho', 'Fiction', 'A fable about following a dream.', 299.00, 10
WHERE NOT EXISTS (SELECT 1 FROM books WHERE title = 'The Alchemist');
