from concurrent.futures import ThreadPoolExecutor

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, select
from sqlalchemy import event, update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from src import api


CUSTOMER = {
    "name": "A Reader", "email": "reader@example.com", "phone": "9876543210",
    "address_line": "1 Story Lane", "city": "Mysuru", "state": "Karnataka",
    "postal_code": "570001", "country": "India",
}


@pytest.fixture
def client(monkeypatch):
    engine = create_engine("sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool)
    @event.listens_for(engine, "connect")
    def enable_foreign_keys(connection, _):
        connection.execute("PRAGMA foreign_keys=ON")
    factory = sessionmaker(bind=engine, expire_on_commit=False)
    monkeypatch.setattr(api, "engine", engine)
    monkeypatch.setattr(api, "SessionLocal", factory)
    monkeypatch.setattr(api, "DATABASE_URL", "sqlite://")
    api.Base.metadata.create_all(engine)
    with TestClient(api.app, raise_server_exceptions=False) as test_client:
        yield test_client
    api.Base.metadata.drop_all(engine)
    engine.dispose()


def payload(items=None, **overrides):
    value = {"items": items if items is not None else [{"book_id": 1, "quantity": 2}], "customer": CUSTOMER}
    value.update(overrides)
    return value


def stock(client, book_id=1):
    return next(book["stock"] for book in client.get("/api/books").json() if book["id"] == book_id)


def test_catalogue_lists_searchable_books(client):
    assert client.get("/api/books?q=alchemist").json()[0]["title"] == "The Alchemist"


def test_successful_order_uses_server_prices_generates_id_and_reduces_stock(client):
    response = client.post("/api/orders", json=payload())
    assert response.status_code == 201
    result = response.json()
    assert result["order_id"]
    assert result["status"] == "CONFIRMED"
    assert result["subtotal"] == 598.0
    assert result["total"] == 598.0
    assert result["items"][0]["line_total"] == 598.0
    assert stock(client) == 6


def test_client_submitted_prices_and_totals_are_rejected_not_trusted(client):
    data = payload()
    data["items"][0]["unit_price"] = 0.01
    data["total"] = 0.01
    response = client.post("/api/orders", json=data)
    assert response.status_code == 400
    assert response.json()["detail"]["code"] == "validation_error"
    assert stock(client) == 8


@pytest.mark.parametrize("data", [
    {"items": [{"book_id": 1, "quantity": 1}]},
    payload(items=[]),
    payload(items=[{"book_id": 1, "quantity": 0}]),
    payload(items=[{"book_id": 1, "quantity": -1}]),
    payload(items=[{"book_id": 1, "quantity": 1.5}]),
    payload(items=[{"book_id": 1, "quantity": "2"}]),
    payload(customer={**CUSTOMER, "email": "not-an-email"}),
    payload(customer={**CUSTOMER, "phone": "letters-only"}),
    payload(customer={**CUSTOMER, "address_line": "   "}),
])
def test_invalid_request_is_rejected_without_stock_changes(client, data):
    response = client.post("/api/orders", json=data)
    assert response.status_code == 400
    assert stock(client) == 8


def test_unknown_book_has_clear_not_found_error(client):
    response = client.post("/api/orders", json=payload(items=[{"book_id": 999, "quantity": 1}]))
    assert response.status_code == 404
    assert response.json()["detail"]["code"] == "book_not_found"
    assert stock(client) == 8


def test_insufficient_stock_returns_conflict_without_partial_order(client):
    response = client.post("/api/orders", json=payload(items=[{"book_id": 3, "quantity": 4}]))
    assert response.status_code == 409
    assert response.json()["detail"]["code"] == "insufficient_stock"
    assert stock(client, 3) == 3
    with api.SessionLocal() as session:
        assert session.scalar(select(api.OrderRow.id)) is None


def test_failed_payment_does_not_confirm_or_reduce_stock(client):
    response = client.post("/api/orders", json=payload(payment_scenario="failure"))
    assert response.status_code == 402
    assert response.json()["detail"]["code"] == "payment_failed"
    assert stock(client) == 8
    with api.SessionLocal() as session:
        assert session.scalar(select(api.OrderRow.id)) is None


def test_successful_orders_have_unique_order_ids(client):
    first = client.post("/api/orders", json=payload(items=[{"book_id": 1, "quantity": 1}])).json()
    second = client.post("/api/orders", json=payload(items=[{"book_id": 2, "quantity": 1}])).json()
    assert first["order_id"] != second["order_id"]


def test_idempotency_key_prevents_duplicate_order_and_stock_decrement(client):
    headers = {"Idempotency-Key": "checkout-once-001"}
    first = client.post("/api/orders", json=payload(), headers=headers)
    second = client.post("/api/orders", json=payload(), headers=headers)
    assert first.status_code == second.status_code == 201
    assert first.json()["order_id"] == second.json()["order_id"]
    assert stock(client) == 6


def test_concurrent_orders_for_last_copy_never_oversell(client):
    with api.SessionLocal() as session:
        last_book = session.get(api.BookRow, 3)
        last_book.stock = 1
        session.commit()
    order = payload(items=[{"book_id": 3, "quantity": 1}])
    with ThreadPoolExecutor(max_workers=2) as pool:
        responses = list(pool.map(lambda _: client.post("/api/orders", json=order), range(2)))
    assert sorted(response.status_code for response in responses) == [201, 409]
    assert stock(client, 3) == 0
    with api.SessionLocal() as session:
        assert len(session.scalars(select(api.OrderRow.id)).all()) == 1


def test_api_error_response_does_not_expose_database_details(client):
    response = client.post("/api/orders", json=payload(customer={**CUSTOMER, "name": ""}))
    assert response.status_code == 400
    assert "detail" in response.json()


def test_order_item_foreign_key_prevents_orphaned_order_records(client):
    response = client.post("/api/orders", json=payload(items=[{"book_id": 1, "quantity": 1}]))
    assert response.status_code == 201
    with api.SessionLocal() as session:
        book = session.get(api.BookRow, 1)
        session.delete(book)
        with pytest.raises(IntegrityError):
            session.commit()
        session.rollback()


def test_database_rejects_negative_stock(client):
    with api.SessionLocal() as session:
        with pytest.raises(IntegrityError):
            session.execute(update(api.BookRow).where(api.BookRow.id == 1).values(stock=-1))
            session.commit()
        session.rollback()
    assert stock(client) == 8


def test_unexpected_server_failure_uses_safe_consistent_json(client, monkeypatch):
    def fail_database():
        raise RuntimeError("private connection detail")

    monkeypatch.setattr(api, "SessionLocal", fail_database)
    response = client.get("/api/books")
    assert response.status_code == 500
    assert response.json()["detail"]["code"] == "internal_error"
    assert "private connection detail" not in response.text
