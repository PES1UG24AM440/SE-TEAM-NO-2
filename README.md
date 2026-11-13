# Online Bookstore — Python Starter

A beginner-friendly starting point for an online bookstore assignment. It demonstrates book search, a shopping cart, stock validation, checkout, automated tests, and GitHub Actions CI.

## Project structure

```text
.
├── .github/
│   └── workflows/
│       └── ci.yml
├── src/
│   ├── __init__.py
│   └── main.py
├── backend/       # Spring Boot service (Java 25)
├── tests/
│   ├── __init__.py
│   └── test_main.py
├── .gitignore
├── LICENSE
├── README.md
└── requirements.txt
```

## Requirements

- Python 3.10 or newer
- Git (for version control)
- A GitHub repository (for pull requests and CI)
- Java 25 and Maven 3.9+ (optional, for the Spring Boot service in `backend/`)

## Setup on Windows

Open PowerShell in the project folder:

```powershell
py -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
```

If PowerShell blocks activation, you can run the environment's Python directly:
`.\.venv\Scripts\python.exe -m pip install -r requirements.txt`

## Run the starter

```powershell
python -m src.main
```

The starter uses an in-memory sample catalogue. Data resets whenever the program stops; no database or real payment processing is connected.

## Run tests

```powershell
python -m pytest -q
```

## Main features in this starter

- Search by book title or author
- View sample catalogue
- Add books to a cart
- Validate positive quantities and stock
- Calculate cart totals
- Checkout and reduce in-memory inventory
- Automated tests and GitHub Actions workflow

## Suggested next steps

1. Add category filtering and a proper command-line or web interface.
2. Add persistent storage (SQLite or MySQL).
3. Save orders and order items in the database.
4. Add user accounts and role-based access for customers/admins.
5. Add input validation, logging, and more tests.
6. Never collect or store raw payment-card details in a student project.

## Branch workflow

```bash
git switch main
git pull origin main
git switch -c feature/book-search
git push -u origin feature/book-search
```

Create other branches from an updated `main` and open pull requests on GitHub. For example: `feature/cart`, `feature/checkout`, and `test/order-workflow`.

## Scope note

The original CLI remains an educational in-memory starter. The Member 4 API described below adds local persistence and transaction-protected checkout. The repository also contains a separate Spring Boot Java service in `backend/`, which supports guest-session order history and cancellation. This project is not a production-ready e-commerce service: customer accounts/authentication, TLS deployment, and real payment integration are not implemented here.

## Spring Boot Java backend

The backend targets Java 25 and uses Spring Boot 3.5.16. Install a Java 25 JDK and Maven 3.9 or newer, then run from the repository root:

```powershell
cd backend
mvn test
```

The Maven test lifecycle runs order workflow regression suites against an isolated in-memory H2 database. They verify successful stock deduction, payment-failure behavior, rollback when any line has insufficient stock, idempotent retries, cancellation stock restoration/retry behavior, inventory-ledger uniqueness, and session-scoped order retrieval. The backend CI job runs this command with Temurin Java 25. Database-backed runtime use still requires a configured MySQL database; connection settings are read from `DB_URL` and `DB_USERNAME` and the schema must match the JPA entities.

## Member 4 cart, checkout, and order API

The original Python CLI remains available. The Member 4 implementation adds a React/Vite interface in `frontend/`. The interface now calls the Spring Boot service in `backend/` (port 8080) through the Vite `/api` proxy; the Python FastAPI service in `src/api.py` is kept as a reference implementation of the same contract.

### Run the frontend against the Spring Boot backend

1. Start MySQL. For a fresh database, run `database/schema.sql` followed by `database/additional_books_seed.sql` to load the full catalogue. The additional seed updates book details without resetting stock for existing IDs. For an existing database, apply the required SQL migrations before starting the backend. Then in `backend/` set `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` and run `mvn spring-boot:run` (serves `http://127.0.0.1:8080`).
2. In `frontend/` run `npm install` and `npm run dev`, then open `http://127.0.0.1:5173`.

`GET /api/books` also accepts `q` (title, author or description) and `category`; `GET /api/books/{id}` returns one book. The Spring service requires an `Idempotency-Key` header on `POST /api/orders` (the React app sends one automatically). Books now include `category` and `description`.

The cart is persisted by the Spring service using an anonymous browser cart ID stored in `localStorage`. `GET /api/cart/{cartId}` returns the current lines, item count, and authoritative server subtotal. `PUT /api/cart/{cartId}/items/{bookId}` accepts `{ "quantity": 2 }`, and `DELETE /api/cart/{cartId}/items/{bookId}` removes a line; `DELETE /api/cart/{cartId}/items` clears it after checkout. The API uses current book prices and stock from the catalogue, rejects quantities below one or above stock, and stores lines in the `cart_lines` table. Cart IDs are random UUIDs; account authentication is not implemented in this project.

### Start the Python reference API locally

In one PowerShell window, from the repository root:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
uvicorn src.api:app --reload --host 127.0.0.1 --port 8000
```

In another window:

```powershell
cd frontend
npm install
npm run dev
```

Open `http://127.0.0.1:5173`. Vite proxies `/api` requests to the local API. Set `VITE_API_BASE_URL` only when the API is hosted on another origin. The API docs are at `http://127.0.0.1:8000/docs`.

### API contract

`GET /api/books?q=<optional title or author query>` returns `{id,title,author,price,stock}` records.

The Spring Boot cart is isolated by the same guest HTTP session as order history:

- `GET /api/cart` returns the session cart and its server-calculated `subtotal`.
- `PUT /api/cart/items/{bookId}` with `{"quantity":2}` sets an item's quantity; quantity must be positive, the book must exist, and requested stock must be available.
- `DELETE /api/cart/items/{bookId}` removes one item, and `DELETE /api/cart` empties the cart.

Each cart response includes current server book prices, per-line totals, and stock. Cart contents are session-scoped and held for the lifetime of the HTTP session; checkout still performs its own authoritative stock and price validation.

`POST /api/orders` accepts:

```json
{
  "items": [{"book_id": 1, "quantity": 2}],
  "customer": {
    "name": "A Reader", "email": "reader@example.com", "phone": "",
    "address_line": "1 Story Lane", "city": "Mysuru", "state": "Karnataka",
    "postal_code": "570001", "country": "India"
  },
  "payment_scenario": "success"
}
```

The optional `Idempotency-Key` header makes a repeated checkout in the same browser session return the original order without decrementing stock twice. The response contains the server-generated `order_id`, `status`, `subtotal`, `shipping`, `tax`, `discount`, `total`, and authoritative line items. Client prices and totals are rejected. `GET /api/orders` lists orders for the current guest session, and `GET /api/orders/{orderId}` returns one order, including its line items and totals. The server-issued HTTP session cookie is the guest identity; the checkout email is contact information and is not used to authorize access. An order belonging to another session returns 404. Cancellation is also restricted to the owning session. Errors use a JSON `detail` object with a stable `code` and message; validation errors also include field paths. Invalid input returns 400, unknown books 404, insufficient stock 409, and simulated declines 402.

The Spring Boot API supports `CONFIRMED` and `CANCELLED` order statuses; the only allowed transition is `CONFIRMED` to `CANCELLED`. `POST /api/orders/{orderId}/cancel` cancels a confirmed order and restores its stock in the same transaction. A repeated cancellation is rejected with 409 and does not restore stock again; an unknown order returns 404.

The Spring Boot backend records stock changes in `inventory_movements`. `quantity_change` is signed (negative for an order deduction, positive for a cancellation restock); each row includes the book, order, movement type, and creation timestamp. Stock updates and ledger rows commit or roll back together. The database enforces at most one movement per order, book, and movement type. Apply `database/migrate_order_session_ownership.sql` to an existing MySQL database before starting the updated backend; pre-existing orders have no session owner and are therefore not exposed through the guest-session retrieval API. Existing databases must also have the `inventory_movements` table from `database/schema.sql`.

The payment simulator has deterministic `success` and `failure` scenarios and accepts no card fields. A failed simulation creates no confirmed order and leaves inventory unchanged. Shipping, tax, and discount are configurable with `BOOKSTORE_SHIPPING`, `BOOKSTORE_TAX_RATE`, and `BOOKSTORE_DISCOUNT`; all default to zero because the SRS leaves those rules for Product Owner confirmation.

### Data and architecture notes

`database/schema.sql` defines the separate `customer_orders` MySQL data model and is not used by the current FastAPI service.

The service uses SQLAlchemy tables for books, orders, order items, and idempotency keys. Local development defaults to a persistent SQLite file (`bookstore.db`); set `DATABASE_URL` to a MySQL 8 SQLAlchemy URL (for example `mysql+pymysql://user:password@host:3306/bookstore`) for the SRS deployment database. `docs/mysql_schema.sql` is the matching MySQL schema. A conditional stock update runs inside the same transaction as order and order-item creation, with nonnegative stock and price constraints. SQLite requests are serialized within one API process for deterministic local concurrency behavior; MySQL uses transactional conditional updates. A MySQL 8 service is installed and running locally, but a root connection without a password was denied and the intended isolated bookstore test database/credentials are not identified. The MySQL migration and concurrency path therefore remain unverified.

The Python CLI's catalogue and cart remain in-memory and reset at process exit. Its console prices use `INR` text so the demo also runs in Windows code pages that cannot encode the rupee symbol. The API catalogue uses database persistence and seeds the four starter books only when the `books` table is empty. The React storefront supports book browsing and details, a wishlist persisted in browser `localStorage`, and the existing cart and checkout flow. Wishlist purchases use the same stock-checked cart action as the catalogue. It does not implement customer authentication, order history, cancellation, seller/admin workflows, or a real payment gateway. Those SRS features remain outside Member 4's assigned issues; customer authentication is also still an SRS-level integration dependency.

### Checks

```powershell
python -m pytest -q
cd frontend
npm run lint
npm test
npm run test:e2e
npm run build
```

Postman requests are in `postman/online-bookstore-checkout.postman_collection.json`. Test design, execution results, and defect status are in `test-reports/`.

## Trial Sprint

Practising Git branches, commits,
pull requests, code review, and merging.
