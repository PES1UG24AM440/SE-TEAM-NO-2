package com.bookstore.orders;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyKeyRepository
        extends JpaRepository<IdempotencyKey, String> {

    @EntityGraph(attributePaths = {"order", "order.items", "order.items.book"})
        Optional<IdempotencyKey> findByKey(String key);
}