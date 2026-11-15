# Local setup: MySQL → Spring Boot → React

Follow these steps in order. Each step has a check; do not move on until it passes.

## 1. MySQL (8.0+)

From the repository root, as a MySQL administrator:

```powershell
mysql -u root -p < database/schema.sql
mysql -u root -p < database/additional_books_seed.sql
mysql -u root -p < database/create_app_user.sql
```

`create_app_user.sql` creates a dedicated `bookstore_app` account (password `Bookstore@123`, local development only) with data-only access to `online_bookstore`. The backend should use this account rather than `root`.

**Check:**

```powershell
mysql -u bookstore_app -p < database/verify_setup.sql
```

Every required table must report `OK`, and `books_in_catalogue` must be greater than 0.

Already have an older `online_bookstore` database? Back it up, then apply `database/migrate_books_for_spring.sql` `database/migrate_order_session_ownership.sql` and `database/migrate_auth_users.sql`, then `database/migrate_seller_listings.sql` (the verify script tells you if the second one is missing).

## 2. Spring Boot backend (Java 25, Maven 3.9+)

Windows PowerShell:

```powershell
cd backend
$env:DB_USERNAME = "bookstore_app"
$env:DB_PASSWORD = "Bookstore@123"
mvn spring-boot:run
```

macOS / Linux:

```bash
cd backend
DB_USERNAME=bookstore_app DB_PASSWORD='Bookstore@123' mvn spring-boot:run
```

**Check:** in a second terminal

```powershell
curl http://localhost:8080/api/books/1
```

returns JSON for *The Alchemist*. `http://localhost:8080/api/books` returns the full catalogue.

### Accounts and roles (Customer, Seller, Administrator)

Browsing, the cart and checkout still work without signing in. The seller dashboard
(`/api/seller/**`) and admin dashboard (`/api/admin/**`) need an account with that role:
not signed in → **401**, wrong role → **403**.

Anyone can register as a Customer or Seller from **Sign in → Create an account**.
Administrators cannot self-register; create a demo admin (and optionally a seller) by setting
environment variables **before** `mvn spring-boot:run`. Pick your own passwords (8+ characters);
they are stored only as BCrypt hashes and are never committed:

```powershell
$env:BOOKSTORE_ADMIN_EMAIL = "admin@bookstore.local"
$env:BOOKSTORE_ADMIN_PASSWORD = "<choose-a-password>"
$env:BOOKSTORE_SELLER_EMAIL = "seller@bookstore.local"
$env:BOOKSTORE_SELLER_PASSWORD = "<choose-a-password>"
```

The accounts are created once; restarting does not change an existing account.

### Seller dashboard (#72)

Sign in with a Seller account and open **Seller dashboard** to add, edit, remove and relist
your own books. "Remove" deactivates the listing: it disappears from the catalogue and cannot
be ordered, but past orders keep it. Editing another seller's listing returns **403**.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/api/seller/books` | Your listings (including removed ones) |
| POST | `/api/seller/books` | Create a listing |
| PUT | `/api/seller/books/{id}` | Edit title, author, category, description, price, stock |
| DELETE | `/api/seller/books/{id}` | Remove (deactivate) |
| POST | `/api/seller/books/{id}/activate` | Relist |

## 3. React frontend (Node 20+)

```powershell
cd frontend
npm install
npm run dev
```

Open <http://127.0.0.1:5173>. Vite proxies `/api` to port 8080.

## 4. Tests

```powershell
cd frontend
npm run lint
npm test
npm run build
npm run test:e2e        # needs steps 1–2 running; uses Microsoft Edge by default
```

To run the end-to-end tests with Chrome instead of Edge: `$env:PLAYWRIGHT_CHANNEL = "chrome"; npm run test:e2e`.

Backend tests use an in-memory H2 database and need no MySQL: `cd backend; mvn test`.

## Troubleshooting MySQL connection errors

| Error in the Spring Boot log | Cause | Fix |
| --- | --- | --- |
| `Access denied for user 'root'@'localhost' (using password: NO)` | `DB_PASSWORD` is not set, so the backend tried `root` with an empty password | Use the `bookstore_app` account and set `DB_USERNAME` / `DB_PASSWORD` as in step 2 (environment variables only last for that terminal window) |
| `Access denied for user 'bookstore_app'@'localhost' (using password: YES)` | Wrong password, or `create_app_user.sql` not run | Re-run `database/create_app_user.sql` as root |
| `Public Key Retrieval is not allowed` | MySQL 8 `caching_sha2_password` over a non-SSL connection | The default `DB_URL` now includes `allowPublicKeyRetrieval=true`; if you override `DB_URL`, keep that parameter |
| `Communications link failure` / `Connection refused` | MySQL service is not running or not on port 3306 | Start the MySQL service (Windows: `services.msc` → MySQL80 → Start) |
| `Unknown database 'online_bookstore'` | Schema not created | Run `database/schema.sql` |
| `Schema-validation: missing table [...]` or `missing column [...]` | Database is older than the code | Run the migration scripts from step 1 |
| `Annotation-specified bean name 'cartController' ... conflicts with existing` | Two cart controllers had the same default bean name | Fixed in this branch: the `com.bookstore.cart` beans are named `cartLineController` / `cartLineService` |
