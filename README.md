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

The original CLI remains an educational in-memory starter. The Member 4 API described below adds local persistence and transaction-protected checkout, but this project is not a production-ready e-commerce service: customer authentication, TLS deployment, order history/cancellation, and real payment integration are not implemented here.

## Member 4 cart, checkout, and order API

The original Python CLI remains available. The Member 4 implementation adds a React/Vite interface in `frontend/` and a Python FastAPI service in `src/api.py`. FastAPI is used as the lightweight Python backend equivalent: no Java/Spring service existed in this repository, and the test plan allows an approved equivalent. The UI calls the live API; it is not a disconnected demo.

### Start locally

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

The optional `Idempotency-Key` header makes a repeated checkout return the original order without decrementing stock twice. The response contains the server-generated `order_id`, `status`, `subtotal`, `shipping`, `tax`, `discount`, `total`, and authoritative line items. Client prices and totals are rejected. Errors use a JSON `detail` object with a stable `code` and message; validation errors also include field paths. Invalid input returns 400, unknown books 404, insufficient stock 409, and simulated declines 402.

The payment simulator has deterministic `success` and `failure` scenarios and accepts no card fields. A failed simulation creates no confirmed order and leaves inventory unchanged. Shipping, tax, and discount are configurable with `BOOKSTORE_SHIPPING`, `BOOKSTORE_TAX_RATE`, and `BOOKSTORE_DISCOUNT`; all default to zero because the SRS leaves those rules for Product Owner confirmation.

### Data and architecture notes

The service uses SQLAlchemy tables for books, orders, order items, and idempotency keys. Local development defaults to a persistent SQLite file (`bookstore.db`); set `DATABASE_URL` to a MySQL 8 SQLAlchemy URL (for example `mysql+pymysql://user:password@host:3306/bookstore`) for the SRS deployment database. `docs/mysql_schema.sql` is the matching MySQL schema. A conditional stock update runs inside the same transaction as order and order-item creation, with nonnegative stock and price constraints. SQLite requests are serialized within one API process for deterministic local concurrency behavior; MySQL uses transactional conditional updates. A MySQL 8 service is installed and running locally, but a root connection without a password was denied and the intended isolated bookstore test database/credentials are not identified. The MySQL migration and concurrency path therefore remain unverified.

The Python CLI's catalogue and cart remain in-memory and reset at process exit. Its console prices use `INR` text so the demo also runs in Windows code pages that cannot encode the rupee symbol. The API catalogue uses database persistence and seeds the four starter books only when the `books` table is empty. The initial React screen implements browse, cart and checkout but does not implement customer authentication, order history, cancellation, seller/admin workflows, or a real payment gateway. Those SRS features remain outside Member 4's assigned issues; customer authentication is also still an SRS-level integration dependency.

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
