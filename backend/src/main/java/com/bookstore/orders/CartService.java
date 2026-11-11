package com.bookstore.orders;

import com.bookstore.books.Book;
import com.bookstore.books.BookRepository;
import com.bookstore.orders.dto.CartResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CartService {

    private static final String CART_SESSION_ATTRIBUTE =
        CartService.class.getName() + ".cart";

    private final BookRepository bookRepository;

    public CartService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    public CartResponse getCart(HttpSession session) {
        synchronized (session) {
            return toResponse(cartFor(session));
        }
    }

    public CartResponse updateQuantity(
            HttpSession session,
            Long bookId,
            int quantity) {
        synchronized (session) {
            Book book = findBook(bookId);
            validateStock(book, quantity);

            CartState cart = cartFor(session);
            cart.quantities.put(bookId, quantity);
            return toResponse(cart);
        }
    }

    public CartResponse removeItem(HttpSession session, Long bookId) {
        synchronized (session) {
            CartState cart = cartFor(session);
            cart.quantities.remove(bookId);
            return toResponse(cart);
        }
    }

    public CartResponse clear(HttpSession session) {
        synchronized (session) {
            cartFor(session).quantities.clear();
            return toResponse(cartFor(session));
        }
    }

    private CartState cartFor(HttpSession session) {
        Object existing = session.getAttribute(CART_SESSION_ATTRIBUTE);
        if (existing == null) {
            CartState cart = new CartState();
            session.setAttribute(CART_SESSION_ATTRIBUTE, cart);
            return cart;
        }
        if (existing instanceof CartState cart) {
            return cart;
        }
        throw new IllegalStateException("The session cart has an invalid value.");
    }

    private Book findBook(Long bookId) {
        return bookRepository.findBookById(bookId)
            .orElseThrow(() -> new OrderException(
                404,
                "book_not_found",
                "Book ID " + bookId + " was not found."
            ));
    }

    private void validateStock(Book book, int quantity) {
        if (quantity > book.getStock()) {
            throw new OrderException(
                409,
                "insufficient_stock",
                "Only " + book.getStock() + " " +
                    (book.getStock() == 1 ? "copy" : "copies") +
                    " of '" + book.getTitle() + "' remain.",
                Map.of(
                    "book_id", book.getId(),
                    "available", book.getStock()
                )
            );
        }
    }

    private CartResponse toResponse(CartState cart) {
        List<CartResponse.CartItemResponse> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (Map.Entry<Long, Integer> line : cart.quantities.entrySet()) {
            Book book = findBook(line.getKey());
            BigDecimal lineTotal = book.getPrice()
                .multiply(BigDecimal.valueOf(line.getValue()))
                .setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineTotal);
            items.add(new CartResponse.CartItemResponse(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getCategory(),
                book.getDescription(),
                line.getValue(),
                book.getStock(),
                book.getPrice(),
                lineTotal
            ));
        }

        return new CartResponse(
            List.copyOf(items),
            subtotal.setScale(2, RoundingMode.HALF_UP)
        );
    }

    private static final class CartState implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final Map<Long, Integer> quantities = new LinkedHashMap<>();
    }
}
