# Frontend catalogue verification — details, sorting, filters, responsive layout

- Branch: `feature/madhu-book-details-sort-filter`
- Author: Madhumitha Dhondi (`MadhumithaDhondi`)
- Date: 14 November 2026

## Scope

| Area | What changed |
| --- | --- |
| MySQL connectivity | `database/create_app_user.sql` (dedicated `bookstore_app` account), `database/verify_setup.sql` (schema health check), JDBC URL now sets `allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC`, 10 s connection timeout, `docs/LOCAL_SETUP.md` troubleshooting table |
| Spring Boot startup | `com.bookstore.cart.CartController` and `com.bookstore.orders.CartController` (and the two `CartService` classes) shared the default bean names `cartController` / `cartService`, which makes Spring fail at startup with `ConflictingBeanDefinitionException`. The `com.bookstore.cart` beans are now named `cartLineController` / `cartLineService`; no endpoints changed |
| Book details | Opens from the title, loads `GET /api/books/{id}` for current price/stock, deep link `#book-<id>`, browser Back support, "Book unavailable" state for unknown IDs |
| Sorting | Featured, title A–Z / Z–A, price low–high / high–low, author A–Z |
| Filters | Min/max price (validated), in-stock only, "Showing X of Y books" count, reset button; combines with existing search and category |
| Responsive | Header nav moves to its own row under 650 px (it previously overflowed at 390 px once Wishlist and Orders were added), filter bar stacks on phones, category chips scroll sideways, number inputs shrink correctly |
| API errors | `api.js` keeps the HTTP status and maps Spring's default 404 body (which has no `detail`) to "Not found." |

## Results

| Check | Command | Result |
| --- | --- | --- |
| Schema health | `mysql -u bookstore_app -p < database/verify_setup.sql` | All 6 tables OK, `owner_session_id` OK, 165 books in 48 categories, connected as `bookstore_app` |
| App-user write access | insert order + order item + stock update + cart line, then roll back | Passed (MySQL 8.0.46) |
| Lint | `npm run lint` | 0 problems |
| Unit tests | `npm test` | 41 passed (3 files; 18 new) |
| Production build | `npm run build` | Built, 0 errors |
| End-to-end | `npm run test:e2e` | 8 passed: 5 new catalogue tests (sort/filter, details/deep link/404, 360 / 768 / 1280 px layouts) + 3 existing checkout and wishlist tests |
| Python tests | `python -m pytest -q` | 37 passed |

### Environment note

These checks ran in a Linux sandbox with MySQL 8.0.46 and Chrome (`PLAYWRIGHT_CHANNEL=chrome`). Maven Central was not reachable from that sandbox, so `mvn test` and `mvn spring-boot:run` could not be executed there. The end-to-end tests ran against a test-only HTTP stub that implements the same `/api` contract over the same MySQL database. **Before merging, a reviewer must run `cd backend; mvn test` and repeat `npm run test:e2e` against the real Spring Boot service** (see `docs/LOCAL_SETUP.md`).

## Fixes to existing tests

- `e2e/wishlist.spec.js`: the mocked `**/api/books**` route returned the whole list for `GET /api/books/1` as well. It now returns a single book for ID URLs, matching the real API. The details view also ignores any response that is not a valid book record, so a malformed response cannot blank the page.
- `e2e/checkout.spec.js` (unchanged): it was failing its 390 px no-horizontal-scroll check because of the header overflow, which is fixed by the responsive header change.
