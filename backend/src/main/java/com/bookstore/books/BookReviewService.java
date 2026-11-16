package com.bookstore.books;

import com.bookstore.orders.OrderException;
import com.bookstore.users.AppUser;
import com.bookstore.users.UserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
public class BookReviewService {

    private final BookRepository books;
    private final BookReviewRepository reviews;
    private final UserRepository users;

    public BookReviewService(BookRepository books, BookReviewRepository reviews, UserRepository users) {
        this.books = books;
        this.reviews = reviews;
        this.users = users;
    }

    @Transactional
    public BookReviewResponse submitReview(Long bookId, CreateReviewRequest request) {
        Book book = requireListedBook(bookId);

        Optional<AppUser> authenticatedUser = resolveAuthenticatedUser();
        Long userId = authenticatedUser.map(AppUser::getId).orElse(null);

        String reviewerName;
        if (request.reviewerName() != null && !request.reviewerName().isBlank()) {
            reviewerName = request.reviewerName().trim();
        } else if (authenticatedUser.isPresent()) {
            reviewerName = authenticatedUser.get().getDisplayName();
        } else {
            reviewerName = "Customer";
        }

        BookReview saved = reviews.saveAndFlush(new BookReview(
                book.getId(),
                userId,
                reviewerName,
                request.rating(),
                request.comment().trim()
        ));
        return BookReviewResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public BookReviewsSummaryResponse getReviewsForBook(Long bookId) {
        Book book = requireListedBook(bookId);

        List<BookReviewResponse> items = reviews.findByBookIdOrderByCreatedAtDescIdDesc(book.getId())
                .stream()
                .map(BookReviewResponse::from)
                .toList();

        long count = items.size();
        double average = 0.0;
        if (count > 0) {
            Double rawAvg = reviews.findAverageRatingByBookId(book.getId());
            if (rawAvg != null) {
                average = BigDecimal.valueOf(rawAvg)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
            }
        }

        return new BookReviewsSummaryResponse(book.getId(), average, count, items);
    }

    private Book requireListedBook(Long bookId) {
        if (bookId == null || bookId <= 0) {
            throw new OrderException(400, "invalid_book_id", "Book ID must be a positive number.");
        }
        return books.findBookById(bookId)
                .filter(Book::isListed)
                .orElseThrow(() -> new OrderException(404, "book_not_found", "Book ID " + bookId + " was not found."));
    }

    private Optional<AppUser> resolveAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return users.findByEmailIgnoreCase(auth.getName());
    }
}