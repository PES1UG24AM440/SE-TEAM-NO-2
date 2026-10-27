"""Small starter implementation for an online bookstore project."""

from dataclasses import dataclass, field


@dataclass
class Book:
    book_id: int
    title: str
    author: str
    price: float
    stock: int = 0


class Bookstore:
    """In-memory catalogue and shopping cart for a beginner project."""

    def __init__(self, books: list[Book] | None = None):
        self.books = {book.book_id: book for book in (books or [])}
        self.cart: dict[int, int] = {}

    def search_books(self, query: str = "") -> list[Book]:
        """Find books by title or author; an empty query returns all books."""
        query = query.strip().casefold()
        return [
            book for book in self.books.values()
            if query in book.title.casefold() or query in book.author.casefold()
        ]

    def add_to_cart(self, book_id: int, quantity: int = 1) -> None:
        """Add a positive quantity if the book exists and enough stock is available."""
        if isinstance(quantity, bool) or not isinstance(quantity, int) or quantity <= 0:
            raise ValueError("Quantity must be greater than zero.")
        book = self.books.get(book_id)
        if book is None:
            raise ValueError(f"Book ID {book_id} was not found.")
        new_quantity = self.cart.get(book_id, 0) + quantity
        if new_quantity > book.stock:
            raise ValueError("Requested quantity exceeds available stock.")
        self.cart[book_id] = new_quantity

    def update_cart_quantity(self, book_id: int, quantity: int) -> None:
        """Replace a cart quantity after validating the book and available stock."""
        if isinstance(quantity, bool) or not isinstance(quantity, int) or quantity <= 0:
            raise ValueError("Quantity must be greater than zero.")
        book = self.books.get(book_id)
        if book is None:
            raise ValueError(f"Book ID {book_id} was not found.")
        if book_id not in self.cart:
            raise ValueError(f"Book ID {book_id} is not in the cart.")
        if quantity > book.stock:
            raise ValueError("Requested quantity exceeds available stock.")
        self.cart[book_id] = quantity

    def remove_from_cart(self, book_id: int) -> None:
        """Remove one item from the cart; a missing item is reported clearly."""
        if book_id not in self.cart:
            raise ValueError(f"Book ID {book_id} is not in the cart.")
        del self.cart[book_id]

    def cart_items(self) -> list[dict]:
        """Return current cart items with names and line subtotals."""
        return [{
            "book_id": book_id,
            "title": self.books[book_id].title,
            "unit_price": self.books[book_id].price,
            "quantity": quantity,
            "line_total": round(self.books[book_id].price * quantity, 2),
        } for book_id, quantity in self.cart.items()]

    def cart_total(self) -> float:
        """Calculate the total price of items currently in the cart."""
        return round(
            sum(self.books[book_id].price * quantity
                for book_id, quantity in self.cart.items()),
            2,
        )

    def checkout(self) -> dict:
        """Validate stock, reduce inventory, return a simple order summary."""
        if not self.cart:
            raise ValueError("Your cart is empty.")

        # Validate all items before changing any stock.
        for book_id, quantity in self.cart.items():
            if quantity > self.books[book_id].stock:
                raise ValueError(f"Insufficient stock for '{self.books[book_id].title}'.")

        items = []
        total = self.cart_total()
        for book_id, quantity in self.cart.items():
            book = self.books[book_id]
            items.append({
                "book_id": book_id,
                "title": book.title,
                "quantity": quantity,
                "unit_price": book.price,
                "line_total": round(book.price * quantity, 2),
            })
            book.stock -= quantity

        self.cart.clear()
        return {"items": items, "total": total, "status": "confirmed"}


def sample_store() -> Bookstore:
    """Return a small catalogue so the project runs immediately."""
    return Bookstore([
        Book(1, "The Alchemist", "Paulo Coelho", 299.00, 8),
        Book(2, "Atomic Habits", "James Clear", 450.00, 5),
        Book(3, "Clean Code", "Robert C. Martin", 650.00, 3),
        Book(4, "The Hobbit", "J. R. R. Tolkien", 399.00, 6),
    ])


def main() -> None:
    store = sample_store()
    print("Welcome to Bookstore Starter")
    print("Available books:")
    for book in store.search_books():
        print(f"{book.book_id}. {book.title} - {book.author} - INR {book.price:.2f} "
              f"({book.stock} in stock)")

    print("\nDemo: adding one copy of Atomic Habits to the cart.")
    store.add_to_cart(2)
    print(f"Cart total: INR {store.cart_total():.2f}")
    order = store.checkout()
    print(f"Order {order['status']}: total INR {order['total']:.2f}")
    print("This is an in-memory educational starter, not a production payment system.")


if __name__ == "__main__":
    main()
