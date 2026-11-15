package com.bookstore.cart;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartLineRepository extends JpaRepository<CartLine, Long> {
    List<CartLine> findAllByCartKeyOrderById(String cartKey);
    Optional<CartLine> findByCartKeyAndBookId(String cartKey, Long bookId);
    void deleteByCartKeyAndBookId(String cartKey, Long bookId);
    void deleteAllByCartKey(String cartKey);
}
