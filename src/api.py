"""REST API for the bookstore checkout workflow.

SQLite is the zero-configuration local/test database. Set DATABASE_URL to a
MySQL 8 SQLAlchemy URL for the agreed deployment database. Inventory updates
use conditional SQL updates inside a transaction, so competing orders cannot
sell the same final copy. Payment is simulated; no card data is accepted.
"""

from __future__ import annotations

import os
import logging
import re
import uuid
import threading
from contextlib import asynccontextmanager
from decimal import Decimal, ROUND_HALF_UP
from typing import Literal

from fastapi import FastAPI, Header, HTTPException
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, EmailStr, Field, StringConstraints, field_validator
from sqlalchemy import CheckConstraint, ForeignKey, Integer, Numeric, String, create_engine, event, select, update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import DeclarativeBase, Mapped, Session, mapped_column, relationship, sessionmaker
from typing_extensions import Annotated


def _database_url() -> str:
    # Persistent local database by default; MySQL is selected through env.
    return os.getenv("DATABASE_URL", "sqlite:///./bookstore.db")


DATABASE_URL = _database_url()
_engine_options = {"pool_pre_ping": True}
if DATABASE_URL.startswith("sqlite"):
    _engine_options["connect_args"] = {"check_same_thread": False}
engine = create_engine(DATABASE_URL, **_engine_options)
if DATABASE_URL.startswith("sqlite"):
    @event.listens_for(engine, "connect")
    def _enable_sqlite_integrity(dbapi_connection, _):
        dbapi_connection.execute("PRAGMA foreign_keys=ON")

SessionLocal = sessionmaker(bind=engine, expire_on_commit=False)
_sqlite_order_lock = threading.Lock()


class Base(DeclarativeBase):
    pass


class BookRow(Base):
    __tablename__ = "books"
    __table_args__ = (
        CheckConstraint("stock >= 0", name="ck_books_stock_nonnegative"),
        CheckConstraint("price >= 0", name="ck_books_price_nonnegative"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    title: Mapped[str] = mapped_column(String(200), nullable=False)
    author: Mapped[str] = mapped_column(String(160), nullable=False)
    price: Mapped[Decimal] = mapped_column(Numeric(10, 2), nullable=False)
    stock: Mapped[int] = mapped_column(Integer, nullable=False)


class OrderRow(Base):
    __tablename__ = "orders"
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    status: Mapped[str] = mapped_column(String(20), nullable=False)
    customer_name: Mapped[str] = mapped_column(String(120), nullable=False)
    email: Mapped[str] = mapped_column(String(254), nullable=False)
    phone: Mapped[str | None] = mapped_column(String(30))
    address_line: Mapped[str] = mapped_column(String(200), nullable=False)
    city: Mapped[str] = mapped_column(String(100), nullable=False)
    state: Mapped[str] = mapped_column(String(100), nullable=False)
    postal_code: Mapped[str] = mapped_column(String(20), nullable=False)
    country: Mapped[str] = mapped_column(String(100), nullable=False)
    subtotal: Mapped[Decimal] = mapped_column(Numeric(10, 2), nullable=False)
    shipping: Mapped[Decimal] = mapped_column(Numeric(10, 2), nullable=False)
    tax: Mapped[Decimal] = mapped_column(Numeric(10, 2), nullable=False)
    discount: Mapped[Decimal] = mapped_column(Numeric(10, 2), nullable=False)
    total: Mapped[Decimal] = mapped_column(Numeric(10, 2), nullable=False)
    items: Mapped[list["OrderItemRow"]] = relationship(back_populates="order", cascade="all, delete-orphan")


class OrderItemRow(Base):
    __tablename__ = "order_items"
    __table_args__ = (CheckConstraint("quantity > 0", name="ck_order_items_quantity_positive"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    order_id: Mapped[str] = mapped_column(ForeignKey("orders.id", ondelete="RESTRICT"), nullable=False)
    book_id: Mapped[int] = mapped_column(ForeignKey("books.id", ondelete="RESTRICT"), nullable=False)
    title: Mapped[str] = mapped_column(String(200), nullable=False)
    quantity: Mapped[int] = mapped_column(Integer, nullable=False)
    unit_price: Mapped[Decimal] = mapped_column(Numeric(10, 2), nullable=False)
    line_total: Mapped[Decimal] = mapped_column(Numeric(10, 2), nullable=False)
    order: Mapped[OrderRow] = relationship(back_populates="items")


def _money(value: Decimal | str | int) -> Decimal:
    return Decimal(value).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


class OrderLine(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    book_id: int = Field(gt=0)
    quantity: int = Field(gt=0)


NonBlank = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]


class CustomerDetails(BaseModel):
    model_config = ConfigDict(extra="forbid")
    name: NonBlank = Field(max_length=120)
    email: EmailStr
    phone: str | None = Field(default=None, max_length=30)
    address_line: NonBlank = Field(max_length=200)
    city: NonBlank = Field(max_length=100)
    state: NonBlank = Field(max_length=100)
    postal_code: NonBlank = Field(max_length=20)
    country: NonBlank = Field(max_length=100)

    @field_validator("phone")
    @classmethod
    def validate_phone(cls, value: str | None) -> str | None:
        if value and not re.fullmatch(r"\+?[0-9 ()-]{7,20}", value.strip()):
            raise ValueError("Enter a valid phone number.")
        return value


class CreateOrder(BaseModel):
    model_config = ConfigDict(extra="forbid")
    items: list[OrderLine] = Field(min_length=1, max_length=50)
    customer: CustomerDetails
    payment_scenario: Literal["success", "failure"] = "success"


def _seed_catalogue(session: Session) -> None:
    if session.scalar(select(BookRow.id).limit(1)) is None:
        session.add_all([
            BookRow(id=1, title="The Alchemist", author="Paulo Coelho", price=Decimal("299.00"), stock=8),
            BookRow(id=2, title="Atomic Habits", author="James Clear", price=Decimal("450.00"), stock=5),
            BookRow(id=3, title="Clean Code", author="Robert C. Martin", price=Decimal("650.00"), stock=3),
            BookRow(id=4, title="The Hobbit", author="J. R. R. Tolkien", price=Decimal("399.00"), stock=6),
        ])
        session.commit()


@asynccontextmanager
async def lifespan(_: FastAPI):
    Base.metadata.create_all(engine)
    with SessionLocal() as session:
        _seed_catalogue(session)
    yield


app = FastAPI(title="Online Bookstore API", version="1.0.0", lifespan=lifespan)
logger = logging.getLogger(__name__)
allowed_origins = [origin.strip() for origin in os.getenv("CORS_ORIGINS", "http://localhost:5173").split(",") if origin.strip()]
app.add_middleware(CORSMiddleware, allow_origins=allowed_origins, allow_methods=["GET", "POST"], allow_headers=["Content-Type", "Idempotency-Key"])


@app.exception_handler(RequestValidationError)
async def validation_error_handler(_, exc: RequestValidationError):
    fields = [{"field": ".".join(str(part) for part in error["loc"] if part != "body"),
               "message": error["msg"]} for error in exc.errors()]
    return JSONResponse(status_code=400, content={
        "detail": {"code": "validation_error", "message": "Please correct the invalid request fields.", "fields": fields}
    })


@app.exception_handler(Exception)
async def internal_error_handler(request, exc: Exception):
    # Log only safe metadata; database exceptions can contain connection details.
    logger.error("Unhandled API error %s at %s", type(exc).__name__, request.url.path)
    return JSONResponse(status_code=500, content={
        "detail": {"code": "internal_error", "message": "An unexpected server error occurred."}
    })

# Fee defaults are deliberately zero until the Product Owner confirms business rules.
SHIPPING = _money(os.getenv("BOOKSTORE_SHIPPING", "0"))
TAX_RATE = Decimal(os.getenv("BOOKSTORE_TAX_RATE", "0"))
DISCOUNT = _money(os.getenv("BOOKSTORE_DISCOUNT", "0"))
if SHIPPING < 0 or TAX_RATE < 0 or DISCOUNT < 0:
    raise ValueError("Shipping, tax rate, and discount configuration must be nonnegative.")


@app.get("/api/books")
def list_books(q: str = ""):
    with SessionLocal() as session:
        rows = session.scalars(select(BookRow).order_by(BookRow.id)).all()
        needle = q.strip().casefold()
        return [{"id": book.id, "title": book.title, "author": book.author,
                 "price": float(book.price), "stock": book.stock}
                for book in rows if not needle or needle in book.title.casefold() or needle in book.author.casefold()]


@app.post("/api/orders", status_code=201)
def create_order(payload: CreateOrder, idempotency_key: str | None = Header(default=None, alias="Idempotency-Key")):
    """Create and confirm an order after simulated payment and atomic stock checks."""
    if idempotency_key is not None and not idempotency_key.strip():
        raise HTTPException(400, detail={"code": "invalid_idempotency_key", "message": "Idempotency-Key cannot be blank."})
    if len({line.book_id for line in payload.items}) != len(payload.items):
        raise HTTPException(400, detail={"code": "duplicate_book", "message": "Include each book once and set its quantity."})

    if DATABASE_URL.startswith("sqlite"):
        # SQLite has a single writer; serialize the read/validate/write sequence
        # in this process so competing test/development requests get a clean 409.
        with _sqlite_order_lock:
            return _create_order_transaction(payload, idempotency_key)
    return _create_order_transaction(payload, idempotency_key)


def _create_order_transaction(payload: CreateOrder, idempotency_key: str | None):
    with SessionLocal() as session:
        # Idempotency key is stored in a simple dedicated table on first use.
        if idempotency_key:
            existing_id = session.scalar(select(IdempotencyRow.order_id).where(IdempotencyRow.key == idempotency_key))
            if existing_id:
                previous = session.get(OrderRow, existing_id)
                if previous:
                    return _order_response(previous)
        books = {line.book_id: session.get(BookRow, line.book_id) for line in payload.items}
        missing = [book_id for book_id, book in books.items() if book is None]
        if missing:
            raise HTTPException(404, detail={"code": "book_not_found", "message": f"Book ID {missing[0]} was not found."})
        for line in payload.items:
            book = books[line.book_id]
            if book.stock < line.quantity:
                raise HTTPException(409, detail={"code": "insufficient_stock", "message": f"Only {book.stock} copy/copies of '{book.title}' remain.", "book_id": book.id, "available": book.stock})

        # The test simulator's deterministic failure mode runs before stock changes.
        if payload.payment_scenario == "failure":
            raise HTTPException(402, detail={"code": "payment_failed", "message": "The payment simulator declined this payment. No order was confirmed."})

        order_id = str(uuid.uuid4())
        subtotal = _money(sum((books[line.book_id].price * line.quantity for line in payload.items), Decimal("0.00")))
        tax = _money(subtotal * TAX_RATE)
        gross = subtotal + SHIPPING + tax
        applied_discount = min(DISCOUNT, gross)
        total = _money(gross - applied_discount)
        customer = payload.customer
        order = OrderRow(id=order_id, status="CONFIRMED", customer_name=customer.name, email=customer.email,
                         phone=customer.phone, address_line=customer.address_line, city=customer.city,
                         state=customer.state, postal_code=customer.postal_code, country=customer.country,
                         subtotal=subtotal, shipping=SHIPPING, tax=tax, discount=applied_discount, total=total)
        session.add(order)
        session.flush()
        for line in payload.items:
            book = books[line.book_id]
            # Atomic predicate prevents overselling under concurrent transactions.
            changed = session.execute(update(BookRow).where(BookRow.id == book.id, BookRow.stock >= line.quantity)
                                      .values(stock=BookRow.stock - line.quantity))
            if changed.rowcount != 1:
                session.rollback()
                with SessionLocal() as check:
                    current = check.get(BookRow, book.id)
                    available = current.stock if current else 0
                    title = current.title if current else book.title
                raise HTTPException(409, detail={"code": "insufficient_stock", "message": f"Only {available} copy/copies of '{title}' remain.", "book_id": book.id, "available": available})
            order.items.append(OrderItemRow(book_id=book.id, title=book.title, quantity=line.quantity,
                                            unit_price=book.price, line_total=_money(book.price * line.quantity)))
        if idempotency_key:
            session.add(IdempotencyRow(key=idempotency_key, order_id=order_id))
        try:
            session.commit()
        except IntegrityError:
            session.rollback()
            if idempotency_key:
                existing_id = session.scalar(select(IdempotencyRow.order_id).where(IdempotencyRow.key == idempotency_key))
                previous = session.get(OrderRow, existing_id) if existing_id else None
                if previous:
                    return _order_response(previous)
            raise HTTPException(409, detail={"code": "order_conflict", "message": "The order could not be completed. Please retry."})
        session.refresh(order)
        return _order_response(order)


class IdempotencyRow(Base):
    __tablename__ = "idempotency_keys"
    key: Mapped[str] = mapped_column(String(120), primary_key=True)
    order_id: Mapped[str] = mapped_column(ForeignKey("orders.id", ondelete="RESTRICT"), nullable=False)


def _order_response(order: OrderRow) -> dict:
    return {"order_id": order.id, "status": order.status, "subtotal": float(order.subtotal),
            "shipping": float(order.shipping), "tax": float(order.tax), "discount": float(order.discount),
            "total": float(order.total), "items": [{"book_id": item.book_id, "title": item.title,
            "quantity": item.quantity, "unit_price": float(item.unit_price), "line_total": float(item.line_total)}
            for item in order.items]}
