package com.bookstore.books;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "book_reviews")
public class BookReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "reviewer_name", nullable = false, length = 120)
    private String reviewerName;

    @Column(nullable = false)
    private Integer rating;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BookReview() {}

    public BookReview(Long bookId, Long userId, String reviewerName, Integer rating, String comment) {
        this.bookId = bookId;
        this.userId = userId;
        this.reviewerName = reviewerName;
        this.rating = rating;
        this.comment = comment;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getBookId() { return bookId; }
    public Long getUserId() { return userId; }
    public String getReviewerName() { return reviewerName; }
    public Integer getRating() { return rating; }
    public String getComment() { return comment; }
    public Instant getCreatedAt() { return createdAt; }
}