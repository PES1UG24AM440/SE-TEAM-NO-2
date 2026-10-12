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

This is an educational starter, not a production-ready e-commerce service. It has no database, authentication, real payment integration, web API, or concurrency-safe inventory management.
