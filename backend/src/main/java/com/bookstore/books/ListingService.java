package com.bookstore.books;

import com.bookstore.orders.OrderException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Creating, editing and (de)activating book listings.
 * Sellers go through the *Own methods, which check ownership on the server (#72);
 * administrators use the unrestricted methods (#73).
 */
@Service
public class ListingService {

    private final BookRepository books;

    public ListingService(BookRepository books) {
        this.books = books;
    }

    // ---- seller (#72) ----

    @Transactional(readOnly = true)
    public List<Book> listForSeller(Long sellerId) {
        return books.findBySellerIdOrderByTitleAsc(sellerId);
    }

    @Transactional
    public Book create(Long sellerId, ListingRequest request) {
        Book book = new Book();
        book.setSellerId(sellerId);
        book.setActive(true);
        apply(book, request);
        return books.saveAndFlush(book);
    }

    @Transactional
    public Book updateOwn(Long sellerId, Long bookId, ListingRequest request) {
        Book book = findOwned(sellerId, bookId);
        apply(book, request);
        return books.saveAndFlush(book);
    }

    @Transactional
    public Book setActiveOwn(Long sellerId, Long bookId, boolean active) {
        Book book = findOwned(sellerId, bookId);
        book.setActive(active);
        return books.saveAndFlush(book);
    }

    // ---- administrator (#73) ----

    @Transactional(readOnly = true)
    public List<Book> listAll() {
        return books.findAllByOrderByTitleAsc();
    }

    @Transactional
    public Book update(Long bookId, ListingRequest request) {
        Book book = find(bookId);
        apply(book, request);
        return books.saveAndFlush(book);
    }

    @Transactional
    public Book setActive(Long bookId, boolean active) {
        Book book = find(bookId);
        book.setActive(active);
        return books.saveAndFlush(book);
    }

    // ---- helpers ----

    private Book findOwned(Long sellerId, Long bookId) {
        Book book = find(bookId);
        if (!Objects.equals(book.getSellerId(), sellerId)) {
            throw new OrderException(403, "not_listing_owner", "You can only change your own listings.");
        }
        return book;
    }

    private Book find(Long bookId) {
        return books.findById(bookId)
            .orElseThrow(() -> new OrderException(404, "book_not_found", "Book ID " + bookId + " was not found."));
    }

    private static void apply(Book book, ListingRequest request) {
        book.setTitle(request.title().trim());
        book.setAuthor(request.author().trim());
        book.setCategory(request.category().trim());
        book.setDescription(request.description() == null || request.description().isBlank()
            ? null : request.description().trim());
        book.setPrice(request.price());
        book.setStock(request.stock());
    }
}
