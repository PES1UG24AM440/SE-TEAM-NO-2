package com.bookstore.books;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/books")
public class BookController {
    private final BookRepository books;

    public BookController(BookRepository books) { this.books = books; }

    @GetMapping
    public List<Book> getBooks(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category) {
        String normalizedQ = (q == null || q.isBlank()) ? null : q.trim();
        String normalizedCategory = (category == null || category.isBlank() || category.equalsIgnoreCase("All"))
                ? null : category.trim();
        return books.search(normalizedQ, normalizedCategory);
    }

    @GetMapping("/{id}")
    public Book getBook(@PathVariable Long id) {
        return books.findBookById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found"));
    }
}
