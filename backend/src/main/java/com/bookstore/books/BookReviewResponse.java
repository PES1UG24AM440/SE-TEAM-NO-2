package com.bookstore.books;

import java.time.Instant;

public record BookReviewResponse(
        Long id,
        Long bookId,
        String reviewerName,
        int rating,
        String comment,
        Instant createdAt
) {
    public static BookReviewResponse from(BookReview review) {
        return new BookReviewResponse(
                review.getId(),
                review.getBookId(),
                review.getReviewerName(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt()
        );
    }
}