# Member 4 Test Report

**Project:** Online Bookstore
**Role:** Test Engineer — Member 4
**Execution date:** 2026-10-27
**Branch:** `feature/member-4-cart-checkout`
**Environment:** Python 3.11; React/Vite; FastAPI; isolated SQLite databases; Microsoft Edge via Playwright. The installed MySQL client is 8.0.33 and the `MySQL80` service is running.

## Architecture and scope under test

The checked-out `main` branch was a Python CLI starter with an in-memory catalogue/cart and eight pytest tests. It had no React client, HTTP API, or database integration. The work preserves the CLI and adds a React/Vite cart and checkout, a FastAPI REST API, SQLAlchemy persistence (SQLite locally, configurable for MySQL), an explicit MySQL schema, frontend/API tests, and Postman artifacts. FastAPI follows the task's lightweight Python equivalent allowance; the current execution used SQLite, not MySQL.

The SRS leaves shipping, tax, discount, and cancellation rules for Product Owner confirmation. This implementation defaults order fees to zero and exposes configuration settings. Authentication, order history/cancellation, seller/admin workflows, and real payment processing are outside this Member 4 checkout implementation.

## Execution summary

| Check | Command / evidence | Result |
|---|---|---|
| Existing CLI smoke | `.venv\Scripts\python.exe -m src.main` | **PASS**, catalogue, cart total and checkout printed successfully. |
| Python unit + API + regression suite | `.venv\Scripts\python.exe -m pytest -q` | **37 passed**, 1 Starlette deprecation warning. |
| Frontend component tests | `npm test` | **4 passed** after the final validation assertion fix. |
| Frontend lint | `npm run lint` | **PASS**, no lint findings. |
| Frontend production build | `npm run build` | **PASS**, Vite 6.4.5 built `dist/`. |
| Browser end-to-end | `npm run test:e2e` | **1 passed** in Microsoft Edge: catalogue/cart, quantity subtotal, remove/empty state, invalid checkout, actual API order, order ID/total, stock decrement, insufficient stock, payment failure/no false confirmation, and mobile-width overflow check. |
| npm production dependency audit | `npm audit --omit=dev` | **0 vulnerabilities**. After upgrading Vitest, `npm install` reported zero vulnerabilities across the full tree. |
| Postman | Not run | **NOT RUN**. Collection and environment are authored only. |
| MySQL 8 verification | `MySQL80` service running; root `SELECT VERSION()` without credentials returned MySQL error 1045 | **BLOCKED**. No approved isolated bookstore test database/credentials were identified; SQLite tests do not prove MySQL migration or concurrency behavior. The existing trial report's 8.0.46 version claim was not reproduced (installed client currently reports 8.0.33). |

Postman collection/environment JSON syntax was checked with PowerShell `ConvertFrom-Json`; this validates import-file syntax only, not execution of the requests.

## Test case results and traceability

The eleven baseline IDs are preserved and mapped to GitHub issues and SRS requirements in [member4-test-cases.md](member4-test-cases.md):

| Test ID | GitHub issue | SRS mapping | Status |
|---|---:|---|---|
| TC-CART-01 | #31 | OBS-F-005 | PASS |
| TC-ORD-01 | #32 | OBS-F-006 | PASS |
| TC-BILL-01 | #33 | OBS-F-007 | PASS |
| TC-PAY-01 | #33 | OBS-F-008 | PASS |
| TC-PAY-02 | #33 | OBS-F-008 | PASS |
| TC-ORD-02 | #33 | OBS-F-009 | PASS |
| TC-STK-01 | #33 | OBS-F-010 | PASS |
| TC-ORD-03 | #33 | OBS-F-011 | PASS |
| TC-CONC-01 | #34 | OBS-NFR-004 | PASS (SQLite, one process) |
| TC-UX-01 | #31/#32 | SRS §6 usability | PASS (desktop and 390×844 check) |
| TC-DATA-01 | #33 | SRS §6 data integrity | PASS (SQLite constraints) |

## API contract exercised

- `GET /api/books?q=<optional title/author query>` returns the catalogue with ID, title, author, price, and stock.
- `POST /api/orders` accepts `items`, `customer`, and the simulator-only `payment_scenario`; optional `Idempotency-Key` prevents duplicate purchase effects.
- Successful checkout returns HTTP 201 with the persisted UUID order ID, `CONFIRMED`, authoritative line items, and server-calculated subtotal/fees/total.
- Invalid input returns 400; unknown books 404; insufficient stock 409; simulator decline 402. Errors return a JSON `detail` containing a stable code and message.
- Request prices/totals are not accepted. Atomic stock decrements and order writes share a transaction. The local SQLite test path serializes requests in one process; the MySQL implementation has not been exercised.

## Defects and remaining blockers

No application defect remained open after the executed tests. Test-harness issues found during initial runs and resolved are in [member4-defects.md](member4-defects.md). Remaining verification limitations:

1. MySQL 8 schema/connection and concurrent transaction behavior need execution against the project's MySQL environment.
2. Product Owner confirmation is needed for nonzero shipping, tax, and discount rules; current defaults are zero.
3. Postman requests have not been run in Postman/Newman.
4. Checkout authentication is an SRS integration requirement that is not present in the original starter or this assigned slice.

## Review status

No changes were pushed and no GitHub issues were closed. Work is on the local feature branch `feature/member-4-cart-checkout`; commit status is reported after final review.
