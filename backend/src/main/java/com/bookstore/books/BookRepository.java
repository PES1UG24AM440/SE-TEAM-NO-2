package com.bookstore.books;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {

    @Query("select b from Book b where " +
           "(:q is null or lower(b.title) like lower(concat('%', :q, '%')) " +
           "or lower(b.author) like lower(concat('%', :q, '%')) " +
           "or lower(coalesce(b.description, '')) like lower(concat('%', :q, '%'))) " +
           "and (:category is null or lower(b.category) = lower(:category)) " +
           "order by b.title asc")
    List<Book> search(
            @Param("q") String q,
            @Param("category") String category
    );

    @Query("select b from Book b where b.id = :id")
    Optional<Book> findBookById(@Param("id") Long id);
    @Modifying
@Transactional
@Query("""
    UPDATE Book b
    SET b.stock = b.stock - :quantity
    WHERE b.id = :bookId AND b.stock >= :quantity
""")
int deductStock(
    @Param("bookId") Long bookId,
    @Param("quantity") int quantity
);

@Modifying
@Transactional
@Query("""
    UPDATE Book b
    SET b.stock = b.stock + :quantity
    WHERE b.id = :bookId
""")
int restoreStock(
    @Param("bookId") Long bookId,
    @Param("quantity") int quantity
);
}
