"""Starter catalogue API used by the cart interface; orders are added in #33."""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from src.main import sample_store

app = FastAPI(title="Online Bookstore API", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://127.0.0.1:5173"],
    allow_methods=["GET"],
    allow_headers=["Content-Type"],
)
catalogue = sample_store()


@app.get("/api/books")
def list_books(q: str = ""):
    books = catalogue.search_books(q)
    return [{
        "id": book.book_id,
        "title": book.title,
        "author": book.author,
        "price": book.price,
        "stock": book.stock,
    } for book in books]
