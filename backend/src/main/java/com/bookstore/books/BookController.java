package com.bookstore.books;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("title", "price");

    private final BookRepository books;

    public BookController(BookRepository books) {
        this.books = books;
    }

    @GetMapping
    public Object getBooks(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "title") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        String normalizedQ = normalize(q);
        String normalizedTitle = normalize(title);
        String normalizedAuthor = normalize(author);
        String normalizedCategory =
                (category == null || category.isBlank() || category.equalsIgnoreCase("All"))
                        ? null : category.trim();

        String normalizedSortBy = validateSortBy(sortBy);
        String normalizedDirection = validateDirection(direction);
        Sort sort = buildSort(normalizedSortBy, normalizedDirection);

        if (page == null && size == null) {
            return books.search(normalizedQ, normalizedTitle, normalizedAuthor, normalizedCategory, sort);
        }

        int resolvedPage = (page == null) ? DEFAULT_PAGE : page;
        int resolvedSize = (size == null) ? DEFAULT_SIZE : size;
        validatePagination(resolvedPage, resolvedSize);

        Pageable pageable = PageRequest.of(resolvedPage, resolvedSize, sort);
        Page<Book> resultPage = books.searchPaged(
                normalizedQ, normalizedTitle, normalizedAuthor, normalizedCategory, pageable);

        return BookPageResponse.from(resultPage, normalizedSortBy, normalizedDirection);
    }

    @GetMapping("/paged")
    public BookPageResponse getPagedBooks(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "title") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        String normalizedQ = normalize(q);
        String normalizedTitle = normalize(title);
        String normalizedAuthor = normalize(author);
        String normalizedCategory =
                (category == null || category.isBlank() || category.equalsIgnoreCase("All"))
                        ? null : category.trim();

        String normalizedSortBy = validateSortBy(sortBy);
        String normalizedDirection = validateDirection(direction);
        validatePagination(page, size);

        Pageable pageable = PageRequest.of(page, size, buildSort(normalizedSortBy, normalizedDirection));
        Page<Book> resultPage = books.searchPaged(
                normalizedQ, normalizedTitle, normalizedAuthor, normalizedCategory, pageable);

        return BookPageResponse.from(resultPage, normalizedSortBy, normalizedDirection);
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
    public ResponseEntity<Object> handleInvalidParameter(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Invalid request parameter '" + ex.getName() + "'"));
    }

    private static String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static void validatePagination(int page, int size) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page index must not be negative");
        }
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page size must be between 1 and " + MAX_PAGE_SIZE
            );
        }
    }

    private static String validateSortBy(String sortBy) {
        String normalized = (sortBy == null || sortBy.isBlank()) ? "title" : sortBy.trim().toLowerCase();
        if (!ALLOWED_SORT_FIELDS.contains(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported sort field '" + sortBy + "'. Allowed fields: title, price"
            );
        }
        return normalized;
    }

    private static String validateDirection(String direction) {
        String normalized = (direction == null || direction.isBlank()) ? "asc" : direction.trim().toLowerCase();
        if (!normalized.equals("asc") && !normalized.equals("desc")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported sort direction '" + direction + "'. Allowed values: asc, desc"
            );
        }
        return normalized;
    }

    private static Sort buildSort(String sortBy, String direction) {
        Sort.Direction dir = direction.equals("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(dir, sortBy).and(Sort.by(Sort.Direction.ASC, "id"));
    }
}