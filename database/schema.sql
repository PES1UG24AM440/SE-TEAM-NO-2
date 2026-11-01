
CREATE DATABASE IF NOT EXISTS online_bookstore
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE online_bookstore;

-- 1. BOOKS
CREATE TABLE IF NOT EXISTS books (
    id INT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    author VARCHAR(160) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    stock INT NOT NULL,

    CONSTRAINT ck_books_stock_nonnegative
        CHECK (stock >= 0),

    CONSTRAINT ck_books_price_nonnegative
        CHECK (price >= 0)
);

-- 2. ORDERS
CREATE TABLE IF NOT EXISTS orders (
    id VARCHAR(36) PRIMARY KEY,
    status VARCHAR(20) NOT NULL,

    customer_name VARCHAR(120) NOT NULL,
    email VARCHAR(254) NOT NULL,
    phone VARCHAR(30) NULL,

    address_line VARCHAR(200) NOT NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    country VARCHAR(100) NOT NULL,

    subtotal DECIMAL(10,2) NOT NULL,
    shipping DECIMAL(10,2) NOT NULL,
    tax DECIMAL(10,2) NOT NULL,
    discount DECIMAL(10,2) NOT NULL,
    total DECIMAL(10,2) NOT NULL
);

-- 3. ORDER ITEMS
CREATE TABLE IF NOT EXISTS order_items (
    id INT PRIMARY KEY AUTO_INCREMENT,
    order_id VARCHAR(36) NOT NULL,
    book_id INT NOT NULL,

    title VARCHAR(200) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    line_total DECIMAL(10,2) NOT NULL,

    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id)
        REFERENCES orders(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_order_items_book
        FOREIGN KEY (book_id)
        REFERENCES books(id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_order_items_quantity_positive
        CHECK (quantity > 0),

    INDEX idx_order_items_order_id (order_id),
    INDEX idx_order_items_book_id (book_id)
);

-- 4. IDEMPOTENCY KEYS
CREATE TABLE IF NOT EXISTS idempotency_keys (
    `key` VARCHAR(120) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL,

    CONSTRAINT fk_idempotency_order
        FOREIGN KEY (order_id)
        REFERENCES orders(id)
        ON DELETE RESTRICT
);

-- 5. SEED BOOKS
INSERT INTO books (id, title, author, price, stock)
VALUES
    (1, 'The Alchemist', 'Paulo Coelho', 299.00, 8),
    (2, 'Atomic Habits', 'James Clear', 450.00, 5),
    (3, 'Clean Code', 'Robert C. Martin', 650.00, 3),
    (4, 'The Hobbit', 'J. R. R. Tolkien', 399.00, 6)
ON DUPLICATE KEY UPDATE
    title = VALUES(title);