import pytest

from src.main import Book, Bookstore, sample_store


def test_search_matches_title_and_author():
    store = sample_store()
    assert [book.title for book in store.search_books("atomic")] == ["Atomic Habits"]
    assert [book.title for book in store.search_books("paulo")] == ["The Alchemist"]


def test_empty_search_returns_all_books():
    assert len(sample_store().search_books()) == 4


def test_add_to_cart_and_total():
    store = sample_store()
    store.add_to_cart(2, 2)
    assert store.cart_total() == 900.00


def test_cannot_add_more_than_stock():
    store = Bookstore([Book(1, "Example", "Author", 100.00, 2)])
    with pytest.raises(ValueError, match="exceeds available stock"):
        store.add_to_cart(1, 3)


def test_cannot_add_unknown_book():
    store = sample_store()
    with pytest.raises(ValueError, match="was not found"):
        store.add_to_cart(999)


def test_checkout_reduces_stock_and_clears_cart():
    store = sample_store()
    store.add_to_cart(1, 2)
    order = store.checkout()
    assert order["status"] == "confirmed"
    assert order["total"] == 598.00
    assert store.books[1].stock == 6
    assert store.cart == {}


def test_checkout_rejects_empty_cart():
    with pytest.raises(ValueError, match="cart is empty"):
        sample_store().checkout()


def test_invalid_quantity_is_rejected():
    with pytest.raises(ValueError, match="greater than zero"):
        sample_store().add_to_cart(1, 0)
