package com.bookstore.books;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {
    @Query("select b from Book b where " +
           "(:q is null or lower(b.title) like lower(concat('%', :q, '%')) " +
           "or lower(b.author) like lower(concat('%', :q, '%')) " +
           "or lower(coalesce(b.description, '')) like lower(concat('%', :q, '%'))) " +
           "and (:category is null or lower(b.category) = lower(:category)) " +
           "order by b.title asc")
    List<Book> search(@Param("q") String q, @Param("category") String category);

    @Query("select b from Book b where b.id = :id")
    Optional<Book> findBookById(@Param("id") Long id);
}
