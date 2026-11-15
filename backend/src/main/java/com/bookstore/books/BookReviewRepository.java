package com.bookstore.books;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookReviewRepository extends JpaRepository<BookReview, Long> {

    List<BookReview> findByBookIdOrderByCreatedAtDescIdDesc(Long bookId);

    long countByBookId(Long bookId);

    @Query("select avg(r.rating) from BookReview r where r.bookId = :bookId")
    Double findAverageRatingByBookId(@Param("bookId") Long bookId);
}