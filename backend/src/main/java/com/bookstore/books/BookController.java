package com.bookstore.books;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookRepository books;

    public BookController(BookRepository books) {
        this.books = books;
    }

    @GetMapping
    public List<Book> getBooks(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String category) {

        String normalizedQ = (q == null || q.isBlank()) ? null : q.trim();
        String normalizedTitle = (title == null || title.isBlank()) ? null : title.trim();
        String normalizedAuthor = (author == null || author.isBlank()) ? null : author.trim();
        String normalizedCategory =
                (category == null || category.isBlank() || category.equalsIgnoreCase("All"))
                        ? null : category.trim();

        return books.search(normalizedQ, normalizedTitle, normalizedAuthor, normalizedCategory);
    }

    @GetMapping("/{id}")
    public Book getBook(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Book ID must be a positive number");
        }
        // Deactivated listings are treated as not found by the public catalogue.
        return books.findBookById(id)
                .filter(Book::isListed)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Book not found"
                        ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleInvalidBookId(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Invalid book ID format: must be a numeric ID"));
    }
}