# Member 4 Test Cases

Execution date: 2026-10-27. These cases retain the IDs from the Online Bookstore Test Plan. Automated execution used isolated SQLite test databases and the seeded sample catalogue. All passing statuses below refer only to the executions recorded in `member4-test-report.md`.

## TC-CART-01 — Cart add, update, remove, and totals

- **GitHub issue / SRS:** #31 / OBS-F-005
- **Preconditions:** API catalogue is available; `The Alchemist` has stock; browser component test mounts the React app with an isolated catalogue response.
- **Steps:** Render the catalogue; add a book; open the bag; increase quantity; inspect line subtotal and total; remove the item; inspect the empty-cart message and continue-shopping action.
- **Expected:** Available books can be added; quantities and subtotals update; removal empties the cart; a zero or above-stock quantity cannot be set.
- **Actual:** React Testing Library verified adding, quantity 1→2, ₹299→₹598 subtotal, and removal. Playwright verified remove → empty cart → continue shopping. API tests separately verified stock and quantity validation.
- **Status:** PASS

## TC-ORD-01 — Checkout required-field and format validation

- **GitHub issue / SRS:** #32 / OBS-F-006
- **Preconditions:** Cart contains one in-stock book; checkout form is open.
- **Steps:** Submit blank fields; enter a malformed email; submit again; enter valid shipping details.
- **Expected:** Invalid checkout does not call the order API; required-field and email errors are shown beside the relevant fields; valid details can proceed.
- **Actual:** Component and browser tests observed required name/address messages and invalid-email feedback; no API order was submitted until the form was valid.
- **Status:** PASS

## TC-BILL-01 — Server-side order total

- **GitHub issue / SRS:** #33 / OBS-F-007
- **Preconditions:** Seeded catalogue has its authoritative price; fee settings use their documented zero defaults.
- **Steps:** Submit two copies of book 1; inspect response line total and total; submit a request containing a client price/total.
- **Expected:** Server calculates from current database prices and configured fees; client price/total is rejected or ignored; response components reconcile.
- **Actual:** API test returned subtotal/total ₹598 for two ₹299 books and rejected extra client price/total fields with HTTP 400. Browser order confirmation matched the API response total. Shipping, tax and discount were zero in this execution.
- **Status:** PASS

## TC-PAY-01 — Simulated payment success

- **GitHub issue / SRS:** #33 / OBS-F-008
- **Preconditions:** Valid order and customer details; sufficient stock; simulator set to `success`.
- **Steps:** Submit an order; inspect status, order ID, line items, total and stock.
- **Expected:** A server-generated order ID and CONFIRMED status are returned; stock decreases by the purchased quantity; confirmation reflects API data.
- **Actual:** API and Playwright created a confirmed order; a non-empty generated UUID was displayed; book 1 stock decreased by one.
- **Status:** PASS

## TC-PAY-02 — Simulated payment failure

- **GitHub issue / SRS:** #33 / OBS-F-008
- **Preconditions:** Valid order and details; sufficient stock; simulator set to `failure`.
- **Steps:** Submit; inspect HTTP response, UI state and stock.
- **Expected:** Failure is explicit; no confirmed order or success screen is shown; stock is unchanged.
- **Actual:** API returned HTTP 402 with `payment_failed`; UI remained in checkout and did not show confirmation; stock was unchanged.
- **Status:** PASS

## TC-ORD-02 — Unique order IDs and duplicate submission handling

- **GitHub issue / SRS:** #33 / OBS-F-009
- **Preconditions:** Two distinct valid orders can be placed; a request can include an `Idempotency-Key`.
- **Steps:** Create two orders; repeat one request with the same idempotency key.
- **Expected:** Distinct orders receive different IDs; an identical retry returns the original ID and does not create another order or decrement stock twice.
- **Actual:** API tests observed distinct UUIDs for two orders and one order/stock decrement for a repeated key.
- **Status:** PASS

## TC-STK-01 — Stock updates and stock conflict

- **GitHub issue / SRS:** #33 / OBS-F-010
- **Preconditions:** Seeded books have known stock; requests are made against an isolated test database.
- **Steps:** Purchase available copies; request more than available; inspect stock and order records.
- **Expected:** Successful purchase decrements stock; insufficient stock returns 409; no partial order is stored; stock is never negative.
- **Actual:** API and browser tests observed a successful decrement, HTTP 409 for an over-stock request, unchanged stock after failure, and database rejection of negative stock.
- **Status:** PASS

## TC-ORD-03 — Order confirmation content

- **GitHub issue / SRS:** #33 / OBS-F-011
- **Preconditions:** Valid checkout succeeds through the actual local API.
- **Steps:** Complete checkout and inspect the confirmation screen against the POST response.
- **Expected:** Confirmation displays the actual server order ID, status, total and purchased books/quantities.
- **Actual:** Playwright captured the POST `/api/orders` response and verified the same order ID and total on the confirmation screen.
- **Status:** PASS

## TC-CONC-01 — Competing orders for the last copy

- **GitHub issue / SRS:** #34 / OBS-NFR-004
- **Preconditions:** Isolated SQLite API database has exactly one copy; two requests run concurrently in the same API process.
- **Steps:** Submit two orders for that final copy concurrently; inspect responses, order count and stock.
- **Expected:** Exactly one order succeeds; the other returns 409; stock becomes zero, never negative.
- **Actual:** The concurrency test returned one HTTP 201 and one HTTP 409, persisted one order, and left stock at zero. This verified the SQLite/local process path only; MySQL concurrency has not been run.
- **Status:** PASS

## TC-UX-01 — Responsive layout and clear validation

- **GitHub issue / SRS:** #31, #32 / SRS §6 usability
- **Preconditions:** Local React UI and API are running in Microsoft Edge.
- **Steps:** Use the catalogue and cart at desktop size; resize to 390×844; open checkout; submit invalid fields.
- **Expected:** Core actions remain usable at desktop/mobile sizes; no horizontal overflow; validation messages are clear.
- **Actual:** Playwright completed the workflow at desktop size, checked no horizontal overflow at 390×844, and observed field-specific validation. No accessibility scanner or contrast audit was run.
- **Status:** PASS

## TC-DATA-01 — Referential and inventory integrity

- **GitHub issue / SRS:** #33 / SRS §6 data integrity
- **Preconditions:** Isolated SQLite database contains books, orders and order items.
- **Steps:** Attempt to delete a book referenced by an order; attempt to set stock below zero.
- **Expected:** Foreign-key and nonnegative-stock constraints reject invalid writes; no orphaned order item is created.
- **Actual:** API tests observed a foreign-key integrity error on referenced-book deletion and a check-constraint integrity error on negative stock.
- **Status:** PASS

## Explicitly unexecuted

- Postman collection requests were authored but not executed; do not interpret their assertions as passing.
- MySQL 8 connection, schema execution and concurrent-order behavior were not run because a MySQL executable/server was unavailable in this workspace.
- Authentication/authorization behavior was not tested or implemented in this Member 4 slice; the existing starter has no authentication module.
