package com.bookstore.books;

import java.util.List;

public record BookReviewsSummaryResponse(
        Long bookId,
        double averageRating,
        long reviewCount,
        List<BookReviewResponse> reviews
) {}