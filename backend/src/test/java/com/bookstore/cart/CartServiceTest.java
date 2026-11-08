package com.bookstore.cart;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.bookstore.books.Book;
import com.bookstore.books.BookRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CartServiceTest {
    private CartLineRepository lines;
    private BookRepository books;
    private CartService service;
    private Book book;
    private final String key = "11111111-1111-4111-8111-111111111111";

    @BeforeEach
    void setUp() {
        lines = mock(CartLineRepository.class);
        books = mock(BookRepository.class);
        service = new CartService(lines, books);
        book = mock(Book.class);
        when(book.getId()).thenReturn(7L);
        when(book.getTitle()).thenReturn("A Book");
        when(book.getAuthor()).thenReturn("An Author");
        when(book.getPrice()).thenReturn(new BigDecimal("12.50"));
        when(book.getStock()).thenReturn(8);
    }

    @Test
    void subtotalUsesCurrentDatabaseBookPrice() {
        when(lines.findAllByCartKeyOrderById(key)).thenReturn(List.of(new CartLine(key, book, 3)));

        CartResponse result = service.get(key);

        assertEquals(new BigDecimal("37.50"), result.subtotal());
        assertEquals(3, result.itemCount());
        assertEquals(new BigDecimal("37.50"), result.items().getFirst().line_total());
    }

    @Test
    void updatePersistsValidQuantityAndReturnsRecalculatedSubtotal() {
        when(books.findBookById(7L)).thenReturn(Optional.of(book));
        when(lines.findByCartKeyAndBookId(key, 7L)).thenReturn(Optional.empty());
        AtomicReference<CartLine> saved = new AtomicReference<>();
        when(lines.saveAndFlush(any(CartLine.class))).thenAnswer(call -> {
            saved.set(call.getArgument(0));
            return saved.get();
        });
        when(lines.findAllByCartKeyOrderById(key)).thenAnswer(call -> List.of(saved.get()));

        CartResponse result = service.update(key, 7L, 2);

        assertEquals(2, result.items().getFirst().quantity());
        assertEquals(new BigDecimal("25.00"), result.subtotal());
    }

    @Test
    void updateRejectsZeroAndOverStockQuantities() {
        when(books.findBookById(7L)).thenReturn(Optional.of(book));

        assertThrows(ResponseStatusException.class, () -> service.update(key, 7L, 0));
        assertThrows(ResponseStatusException.class, () -> service.update(key, 7L, 9));
        verify(lines, never()).saveAndFlush(any());
    }
}
