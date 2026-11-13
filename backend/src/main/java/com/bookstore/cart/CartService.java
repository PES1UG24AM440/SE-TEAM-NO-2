package com.bookstore.cart;

import com.bookstore.books.Book;
import com.bookstore.books.BookRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CartService {
    private static final Pattern CART_KEY = Pattern.compile("[0-9a-fA-F-]{36}");
    private final CartLineRepository lines;
    private final BookRepository books;

    public CartService(CartLineRepository lines, BookRepository books) { this.lines = lines; this.books = books; }

    @Transactional(readOnly = true)
    public CartResponse get(String key) { return snapshot(validKey(key)); }

    @Transactional
    public CartResponse update(String key, Long bookId, Integer quantity) {
        key = validKey(key);
        if (quantity == null || quantity < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be at least 1.");
        Book book = books.findBookById(bookId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found."));
        if (quantity > book.getStock()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Requested quantity exceeds available stock.");
        final String cartKey = key;
        CartLine line = lines.findByCartKeyAndBookId(cartKey, bookId).orElseGet(() -> new CartLine(cartKey, book, quantity));
        line.setQuantity(quantity);
        lines.saveAndFlush(line);
        return snapshot(cartKey);
    }

    @Transactional
    public CartResponse remove(String key, Long bookId) {
        String cartKey = validKey(key);
        lines.deleteByCartKeyAndBookId(cartKey, bookId);
        return snapshot(cartKey);
    }

    @Transactional
    public CartResponse clear(String key) {
        String cartKey = validKey(key);
        lines.deleteAllByCartKey(cartKey);
        return snapshot(cartKey);
    }

    private CartResponse snapshot(String key) {
        List<CartResponse.CartItem> items = lines.findAllByCartKeyOrderById(key).stream().map(line -> {
            Book book = line.getBook();
            BigDecimal lineTotal = book.getPrice().multiply(BigDecimal.valueOf(line.getQuantity())).setScale(2, RoundingMode.HALF_UP);
            return new CartResponse.CartItem(book.getId(), book.getTitle(), book.getAuthor(), book.getPrice(), line.getQuantity(), lineTotal, book.getStock());
        }).toList();
        BigDecimal subtotal = items.stream().map(CartResponse.CartItem::line_total).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        int count = items.stream().mapToInt(CartResponse.CartItem::quantity).sum();
        return new CartResponse(items, count, subtotal);
    }

    private String validKey(String key) {
        if (key == null || !CART_KEY.matcher(key).matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid cart ID is required.");
        return key;
    }
}
