package com.bookstore.books;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

@RestController
@RequestMapping("/api/books/{bookId}/reviews")
public class BookReviewController {

    private final BookReviewService reviewService;

    public BookReviewController(BookReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public BookReviewsSummaryResponse getReviews(@PathVariable Long bookId) {
        return reviewService.getReviewsForBook(bookId);
    }

    @PostMapping
    public ResponseEntity<BookReviewResponse> addReview(
            @PathVariable Long bookId,
            @Valid @RequestBody CreateReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviewService.submitReview(bookId, request));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleInvalidBookId(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("detail", Map.of(
                        "code", "invalid_book_id",
                        "message", "Book ID must be a valid number."
                )));
    }
}